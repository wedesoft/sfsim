(ns sfsim.astro
    "NASA JPL interpolation for pose of celestical objects (see https://rhodesmill.org/skyfield/ for original code and more)"
    (:require [clojure.core.memoize :as z]
              [clojure.string :refer (join)]
              [malli.core :as m]
              [fastmath.vector :refer (add mult sub vec3)]
              [fastmath.matrix :refer (mat3x3 mulm)]
              [gloss.core :refer (compile-frame ordered-map string finite-block finite-frame repeated prefix sizeof)]
              [gloss.io :refer (decode)]
              [sfsim.matrix :refer (fvec3 rotation-x rotation-z fmat3)])
    (:import [java.nio ByteBuffer]
             [java.nio.file Paths StandardOpenOption]
             [java.nio.channels FileChannel FileChannel$MapMode]))

(set! *unchecked-math* true)
(set! *warn-on-reflection* true)

; Code ported from python-skyfield
; See https://rhodesmill.org/skyfield/

(def T0 2451545.0)  ; noon of 1st January 2000
(def s-per-day 86400.0)  ; seconds per day
(def AU-KM 149597870.700)  ; astronomical unit in km
(def ASEC2RAD 4.848136811095359935899141e-6)  ; convert arcseconds (360 * 60 * 60) to radians (2 * PI)

(defn map-file-to-buffer
  "Map file to read-only byte buffer"
  {:malli/schema [:=> [:cat :string] :some]}
  [filename]
  (let [path        (Paths/get filename (make-array String 0))
        option-read (into-array StandardOpenOption [StandardOpenOption/READ])
        channel     (FileChannel/open path option-read)
        size        (.size channel)
        buffer      (.map channel FileChannel$MapMode/READ_ONLY 0 size)]
    buffer))

(def record-size 1024)

(def daf-header-frame
  (compile-frame
    (ordered-map ::locidw       (string :us-ascii :length 8)
                 ::num-doubles  :int32-le
                 ::num-integers :int32-le
                 ::locifn       (string :us-ascii :length 60)
                 ::forward      :int32-le
                 ::backward     :int32-le
                 ::free         :int32-le
                 ::locfmt       (string :us-ascii :length 8)
                 ::prenul       (finite-block 603)
                 ::ftpstr       (finite-frame 28 (repeated :ubyte :prefix :none)))))

(def daf-comment-frame
  (compile-frame (string :us-ascii :length 1000)))

(defn daf-descriptor-frame
  "Create codec frame for parsing data descriptor"
  {:malli/schema [:=> [:cat :int :int] :some]}
  [num-doubles num-integers]
  (let [summary-length (+ (* 8 num-doubles) (* 4 num-integers))
        padding        (mod (- summary-length) 8)]
    (compile-frame
      (ordered-map ::doubles  (repeat num-doubles :float64-le)
                   ::integers (repeat num-integers :int32-le)
                   ::padding  (finite-block padding)))))

(defn daf-descriptors-frame
  "Create codec frame for parsing a descriptor block"
  {:malli/schema [:=> [:cat :int :int] :some]}
  [num-doubles num-integers]
  (let [descriptor-frame (daf-descriptor-frame num-doubles num-integers)]
    (compile-frame
      (ordered-map ::next-number     :float64-le
                   ::previous-number :float64-le
                   ::descriptors     (repeated descriptor-frame
                                               :prefix (prefix :float64-le long double))))))

(defn daf-source-names-frame
  "Create codec frame for parsing block of source names"
  {:malli/schema [:=> [:cat :int] :some]}
  [n]
  (compile-frame (repeat n (string :us-ascii :length 40))))

(def coefficient-layout-frame
  (compile-frame
    (ordered-map ::init :float64-le
                 ::intlen :float64-le
                 ::rsize :float64-le
                 ::n :float64-le)))

(defn coefficient-frame
  "Return codec frame for parsing a set of coefficients"
  {:malli/schema [:=> [:cat :int :int] :some]}
  [rsize component-count]
  (let [coefficient-count (/ (- rsize 2) component-count)]
    (compile-frame (concat [:float64-le :float64-le] (repeat component-count (repeat coefficient-count :float64-le))))))

(defn decode-record
  "Decode a record using the specified frame"
  {:malli/schema [:=> [:cat :some :some :int] :some]}
  [buffer frame index]
  (let [record (byte-array record-size)]
    (.position ^ByteBuffer buffer ^long (* (dec index) record-size))
    (.get ^ByteBuffer buffer record)
    (decode frame record false)))

(def daf-header (m/schema [:map [::locidw :string] [::num-doubles :int] [::num-integers :int] [::locifn :string]
                                [::forward :int] [::backward :int] [::free :int] [::locfmt :string] [::prenul :some]
                                [::ftpstr [:vector :int]]]))

(defn read-daf-header
  "Read DAF header from byte buffer"
  {:malli/schema [:=> [:cat :some] daf-header]}
  [buffer]
  (decode-record buffer daf-header-frame 1))

(defn check-endianness
  "Check endianness of DAF file"
  {:malli/schema [:=> [:cat [:map [::locfmt :string]]] :boolean]}
  [header]
  (= (::locfmt header) "LTL-IEEE"))

(def ftp-str "FTPSTR:\r:\n:\r\n:\r\u0000:\u0081:\u0010\u00ce:ENDFTP")

(defn check-ftp-str
  "Check FTP string of DAF file"
  {:malli/schema [:=> [:cat [:map [::ftpstr [:sequential :int]]]] :boolean]}
  [header]
  (= (::ftpstr header) (map int ftp-str)))

(defn read-daf-comment
  "Read DAF comment"
  {:malli/schema [:=> [:cat daf-header :some] :string]}
  [header buffer]
  (let [comment-lines (mapv #(decode-record buffer daf-comment-frame %) (range 2 (::forward header)))
        joined-lines  (join comment-lines)
        delimited     (subs joined-lines 0 (clojure.string/index-of joined-lines \o004))
        with-newlines (clojure.string/replace delimited \o000 \newline)]
    with-newlines))

(def daf-descriptor (m/schema [:map [::doubles [:vector :double]] [::integers [:vector :int]]]))
(def daf-descriptors (m/schema [:map [::next-number :double] [::previous-number :double]
                                     [::descriptors [:vector daf-descriptor]]]))

(defn read-daf-descriptor
  "Read descriptors for following data"
  {:malli/schema [:=> [:cat daf-header :int :some] daf-descriptors]}
  [header index buffer]
  (let [num-doubles  (::num-doubles header)
        num-integers (::num-integers header)]
    (decode-record buffer (daf-descriptors-frame num-doubles num-integers) index)))

(defn read-source-names
  "Read source name data"
  {:malli/schema [:=> [:cat :int :int :some] [:sequential :string]]}
  [index n buffer]
  (mapv clojure.string/trim (decode-record buffer (daf-source-names-frame n) index)))

(def daf-summary [:map [::doubles [:vector :double]] [::integers [:vector :int]] [::source :string]])

(defn read-daf-summaries
  "Read sources and descriptors to get summaries"
  {:malli/schema [:=> [:cat :map :int :some] [:sequential daf-summary]]}
  [header index buffer]
  (let [summaries   (read-daf-descriptor header index buffer)
        next-number (long (::next-number summaries))
        descriptors (::descriptors summaries)
        n           (count (::descriptors summaries))
        sources     (read-source-names (inc index) n buffer)
        results     (map (fn [source descriptor] (assoc descriptor ::source source)) sources descriptors)]
    (if (zero? next-number)
      results
      (concat results (read-daf-summaries header next-number buffer)))))

(def spk-segment (m/schema [:map [::source :string] [::start-second :double] [::end-second :double] [::target :int]
                                 [::center :int] [::frame :int] [::data-type :int] [::start-i :int] [::end-i :int]]))

(defn summary->spk-segment
  "Convert DAF summary to SPK segment"
  {:malli/schema [:=> [:cat daf-summary] spk-segment]}
  [summary]
  (let [source                                        (::source summary)
        [start-second end-second]                     (::doubles summary)
        [target center frame data-type start-i end-i] (::integers summary)]
    {::source       source
     ::start-second start-second
     ::end-second   end-second
     ::target       target
     ::center       center
     ::frame        frame
     ::data-type    data-type
     ::start-i      start-i
     ::end-i        end-i}))

(def spk-lookup-table (m/schema [:map-of [:tuple :int :int] spk-segment]))

(defn spk-segment-lookup-table
  "Make a lookup table for pairs of center and target to lookup SPK summaries"
  {:malli/schema [:=> [:cat :map :some] [:map-of [:tuple :int :int] :map]]}
  [header buffer]
  (let [summaries (read-daf-summaries header (::forward header) buffer)
        segments  (map summary->spk-segment summaries)]
    (reduce (fn [lookup segment] (assoc lookup [(::center segment) (::target segment)] segment)) {} segments)))

(defn convert-to-long
  "Convert values with specified keys to long integer"
  {:malli/schema [:=> [:cat :map [:vector :keyword]] [:map-of :keyword :some]]}
  [hashmap keys_]
  (reduce (fn [hashmap key_] (update hashmap key_ long)) hashmap keys_))

(def coefficient-layout (m/schema [:map [::init :double] [::intlen :double] [::rsize :int] [::n :int]]))

(defn read-coefficient-layout
  "Read layout information from end of segment"
  {:malli/schema [:=> [:cat :map :some] coefficient-layout]}
  [segment buffer]
  (let [info (byte-array (sizeof coefficient-layout-frame))]
    (.position ^ByteBuffer buffer ^long (* 8 (- (::end-i segment) 4)))
    (.get ^ByteBuffer buffer info)
    (convert-to-long (decode coefficient-layout-frame info) [::rsize ::n])))

(defn read-interval-coefficients
  "Read coefficient block with specified index from segment"
  {:malli/schema [:=> [:cat :map :map :int :some] [:sequential [:sequential :double]]]}
  [segment layout index buffer]
  (let [component-count ({2 3 3 6} (::data-type segment))
        frame           (coefficient-frame (::rsize layout) component-count)
        data            (byte-array (sizeof frame))]
    (.position ^ByteBuffer buffer ^long (+ (* 8 (dec (::start-i segment))) (* index (sizeof frame))))
    (.get ^ByteBuffer buffer data)
    (reverse (apply map vector (drop 2 (decode frame data))))))

(defn chebyshev-polynomials
  "Chebyshev polynomials"
  {:malli/schema [:=> [:cat [:sequential :some] :double :some] :some]}
  [coefficients s zero]
  (let [s2      (* 2.0 s)
        [w0 w1] (reduce (fn [[w0 w1] c] [(-> w0 (mult s2) (sub w1) (add c)) w0])
                        [zero zero]
                        (butlast coefficients))]
    (add (last coefficients) (sub (mult w0 s) w1))))

(defn interval-index-and-position
  "Compute interval index and position (s) inside for given timestamp"
  {:malli/schema [:=> [:cat [:map [::init :double] [::intlen :double] [::n :int]] :double] [:tuple :int :double]]}
  [layout tdb]
  (let [init   (::init layout)
        intlen (::intlen layout)
        n      (::n layout)
        t      (- (* (- tdb T0) s-per-day) init)
        index  (min (max (long (quot t intlen)) 0) (dec n))
        offset (- t (* index intlen))
        s      (- (/ (* 2.0 offset) intlen) 1.0)]
    [index s]))

(defn map-vec3
  "Convert list of vectors to list of vec3 objects"
  {:malli/schema [:=> [:cat [:sequential [:vector :double]]] [:sequential fvec3]]}
  [lst]
  (map #(apply vec3 %) lst))

(defn make-spk-document
  "Create object with segment information"
  {:malli/schema [:=> [:cat :string] [:map [::header daf-header] [::lookup spk-lookup-table] [::buffer :some]]]}
  [filename]
  (let [buffer (map-file-to-buffer filename)
        header (read-daf-header buffer)
        lookup (spk-segment-lookup-table header buffer)]
    (when (not (check-ftp-str header))
      (throw (RuntimeException. "FTPSTR has wrong value")))
    (when (not (check-endianness header))
      (throw (RuntimeException. "File endianness not implemented!")))
    {::header header
     ::lookup lookup
     ::buffer buffer}))

(defn make-segment-interpolator
  "Create object for interpolation of a particular target position"
  {:malli/schema [:=> [:cat [:map [::header daf-header] [::lookup spk-lookup-table] [::buffer :some]] :int :int] ifn?]}
  [spk center target]
  (let [buffer  (::buffer spk)
        lookup  (::lookup spk)
        segment (get lookup [center target])
        layout  (read-coefficient-layout segment buffer)
        cache   (z/lru (fn [index] (map-vec3 (read-interval-coefficients segment layout index buffer))) :lru/threshold 8)]
    (fn [tdb]
        (let [[index s]    (interval-index-and-position layout tdb)
              coefficients (cache index)]
          (chebyshev-polynomials coefficients s (vec3 0 0 0))))))

(def date (m/schema [:map [:year :int] [:month :int] [:day :int]]))

(defn julian-date
  "Convert calendar date to Julian date"
  {:malli/schema [:=> [:cat date] :int]}
  [{:keys [year month day]}]
  (let [g (- (+ year 4716) (if (<= month 2) 1 0))
        f (mod (+ month 9) 12)
        e (- (+ (quot (* 1461 g) 4) day) 1402)
        J (+ e (quot (+ (* 153 f) 2) 5))]
    (+ J (- 38 (quot (* (quot (+ g 184) 100) 3) 4)))))

(defn calendar-date
  "Convert Julian date to calendar date"
  {:malli/schema [:=> [:cat :int] date]}
  [jd]
  (let [f (+ jd 1401)
        f (+ f (- (quot (* (quot (+ (* 4 jd) 274277) 146097) 3) 4) 38))
        e (+ (* 4 f) 3)
        g (quot (mod e 1461) 4)
        h (+ (* 5 g) 2)
        day (inc (quot (mod h 153) 5))
        month (inc (mod (+ (quot h 153) 2) 12))
        year (+ (- (quot e 1461) 4716) (quot (- (+ 12 2) month) 12))]
    {:year year :month month :day day}))

; See python-skyfield precessionlib.compute_precession

(defn psi-a
  "Compute Psi angle for Earth precession given centuries since 2000"
  {:malli/schema [:=> [:cat :double] :double]}
  [t]
  (-> t (* -0.0000000951) (+ 0.000132851) (* t) (- 0.00114045) (* t) (- 1.0790069) (* t) (+ 5038.481507) (* t)))

(def eps0 84381.406)

(defn omega-a
  "Compute Omega angle for Earth precession given centuries since 2000"
  {:malli/schema [:=> [:cat :double] :double]}
  [t]
  (-> t (* 0.0000003337) (- 0.000000467) (* t) (- 0.00772503) (* t) (+ 0.0512623) (* t) (- 0.025754) (* t) (+ eps0)))

(defn chi-a
  "Compute Chi angle for Earth precession given centuries since 2000"
  {:malli/schema [:=> [:cat :double] :double]}
  [t]
  (-> t (* -0.0000000560) (+ 0.000170663) (* t) (- 0.00121197) (* t) (- 2.3814292) (* t) (+ 10.556403) (* t)))

(defn compute-precession
  "Compute precession matrix for Earth given Julian day"
  {:malli/schema [:=> [:cat :double] fmat3]}
  [tdb]
  (let [t          (/ (- tdb T0) 36525.0)
        r3-chi-a   (rotation-z (* (- (chi-a t)) ASEC2RAD))
        r1-omega-a (rotation-x (* (omega-a t) ASEC2RAD))
        r3-psi-a   (rotation-z (* (psi-a t) ASEC2RAD))
        r1-eps0    (rotation-x (* (- eps0) ASEC2RAD))]
    (mulm r3-chi-a (mulm r1-omega-a (mulm r3-psi-a r1-eps0)))))

; Compute Greenwich mean sidereal time.
; See python-skyfield earthlib.earth_rotation_angle

(defn earth-rotation-angle
  "Compute Earth rotation angle as a value between 0 and 1"
  {:malli/schema [:=> [:cat :double] :double]}
  [jd-ut]
  (let [th (+ 0.7790572732640 (* 0.00273781191135448 (- jd-ut T0)))]
    (mod (+ (mod th 1.0) (mod jd-ut 1.0)) 1.0)))

(defn sidereal-time
  "Compute Greenwich Mean Sidereal Time (GMST) in hours"
  [jd-ut]
  (let [theta (earth-rotation-angle jd-ut)
        t     (/ (- jd-ut T0) 36525.0)
        st    (-> t (* -0.0000000368) (- 0.000029956)
                    (* t) (- 0.00000044)
                    (* t) (+ 1.3915817)
                    (* t) (+ 4612.156534)
                    (* t) (+ 0.014506))]
    (mod (+ (/ st 54000.0) (* theta 24.0)) 24.0)))

; Conversion matrix from ICRS to J2000.
; See python-skyfield framelib.ICRS_to_J2000

(defn- build_matrix []
  (let [xi0  (* -0.0166170 ASEC2RAD)
        eta0 (* -0.0068192 ASEC2RAD)
        da0  (* -0.01460   ASEC2RAD)
        yx   (- da0)
        zx   xi0
        xy   da0
        zy   eta0
        xz   (- xi0)
        yz   (- eta0)
        xx   (- 1.0 (* 0.5 (+ (* yx yx) (* zx zx))))
        yy   (- 1.0 (* 0.5 (+ (* yx yx) (* zy zy))))
        zz   (- 1.0 (* 0.5 (+ (* zy zy) (* zx zx))))]
    (mat3x3 xx xy xz, yx yy yz, zx zy zz)))

(def ICRS-to-J2000 (build_matrix))

(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
