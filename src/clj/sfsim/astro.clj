;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.astro
  "NASA JPL interpolation for pose of celestical objects (see https://rhodesmill.org/skyfield/ for original code and more)"
  (:require
    [clojure.core.memoize :as z]
    [clojure.math :refer (PI)]
    [clojure.string :refer (join)]
    [fastmath.matrix :refer (mat3x3 mulm inverse eye transpose rotation-matrix-3d-x rotation-matrix-3d-y rotation-matrix-3d-z)]
    [fastmath.vector :refer (add mult sub vec3)]
    [gloss.core :refer (compile-frame ordered-map string finite-block finite-frame repeated prefix sizeof)]
    [gloss.io :refer (decode)]
    [instaparse.core :as insta]
    [instaparse.transform :refer (transform)]
    [malli.core :as m]
    [sfsim.matrix :refer (fvec3 fmat3)])
  (:import
    (fastmath.vector
      Vec3)
    (fastmath.matrix
      Mat3x3)
    (java.nio
      ByteBuffer)
    (java.nio.channels
      FileChannel
      FileChannel$MapMode)
    (java.nio.file
      Paths
      StandardOpenOption)))


(set! *unchecked-math* :warn-on-boxed)
(set! *warn-on-reflection* true)


;; Code ported from python-skyfield
;; See https://rhodesmill.org/skyfield/

(def T0 2451545.0)  ; noon of 1st January 2000
(def S-PER-DAY 86400.0)  ; seconds per day
(def AU-KM 149597870.700)  ; astronomical unit in km
(def DEGREES2RAD (/ (* 2 PI) 360.0))  ; convert degrees (360) to radians (2 * PI)
(def ASEC2RAD (/ (* 2.0 PI) 360.0 60.0 60.0))  ; convert arcseconds (360 * 60 * 60) to radians (2 * PI)

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
                 ::ftpstr       (finite-frame 28 (repeated :ubyte :prefix :none))
                 ::pstnul       (finite-block 297))))


(def daf-comment-frame
  (compile-frame (string :us-ascii :length 1000)))


(defn daf-descriptor-frame
  "Create codec frame for parsing data descriptor"
  [^long num-doubles ^long num-integers]
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
  [^long rsize ^long component-count]
  (let [coefficient-count (/ (- rsize 2) component-count)]
    (compile-frame (concat [:float64-le :float64-le] (repeat component-count (repeat coefficient-count :float64-le))))))


(defn decode-record
  "Decode a record using the specified frame"
  [buffer frame ^long index]
  (let [record (byte-array record-size)]
    (.position ^ByteBuffer buffer ^long (* (dec index) ^long record-size))
    (.get ^ByteBuffer buffer record)
    (decode frame record false)))


(def daf-header
  (m/schema [:map [::locidw :string] [::num-doubles :int] [::num-integers :int] [::locifn :string]
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
  (= (::ftpstr header) (mapv int ftp-str)))


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


(def daf-descriptors
  (m/schema [:map [::next-number :double] [::previous-number :double]
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
  [header ^long index buffer]
  (let [summaries   (read-daf-descriptor header index buffer)
        next-number (long (::next-number summaries))
        descriptors (::descriptors summaries)
        n           (count (::descriptors summaries))
        sources     (read-source-names (inc index) n buffer)
        results     (mapv (fn [source descriptor] (assoc descriptor ::source source)) sources descriptors)]
    (if (zero? next-number)
      results
      (concat results (read-daf-summaries header next-number buffer)))))


(def spk-segment
  (m/schema [:map [::source :string] [::start-second :double] [::end-second :double] [::target :int]
             [::center :int] [::frame :int] [::data-type :int] [::start-i :int] [::end-i :int]]))


(defn- summary->segment
  "Common code for DAF summary conversions"
  {:malli/schema [:=> [:cat daf-summary] :map]}
  [summary integer-keys]
  (let [source                    (::source summary)
        [start-second end-second] (::doubles summary)
        integers                  (::integers summary)]
    (apply assoc
           {::source       source
            ::start-second start-second
            ::end-second   end-second}
           (interleave integer-keys integers))))


(defn summary->spk-segment
  "Convert DAF summary to SPK segment"
  {:malli/schema [:=> [:cat daf-summary] spk-segment]}
  [summary]
  (summary->segment summary [::target ::center ::frame ::data-type ::start-i ::end-i]))


(def pck-segment
  (m/schema [:map [::source :string] [::start-second :double] [::end-second :double] [::target :int]
             [::frame :int] [::data-type :int] [::start-i :int] [::end-i :int]]))


(defn summary->pck-segment
  "Convert DAF summary to PCK segment"
  {:malli/schema [:=> [:cat daf-summary] pck-segment]}
  [summary]
  (summary->segment summary [::target ::frame ::data-type ::start-i ::end-i]))


(def spk-lookup-table (m/schema [:map-of [:tuple :int :int] spk-segment]))


(defn spk-segment-lookup-table
  "Make a lookup table for pairs of center and target to lookup SPK summaries"
  {:malli/schema [:=> [:cat :map :some] [:map-of [:tuple :int :int] :map]]}
  [header buffer]
  (let [summaries (read-daf-summaries header (::forward header) buffer)
        segments  (mapv summary->spk-segment summaries)]
    (reduce (fn [lookup segment] (assoc lookup [(::center segment) (::target segment)] segment)) {} segments)))


(def pck-lookup-table (m/schema [:map-of :int pck-segment]))


(defn pck-segment-lookup-table
  "Make a lookup table for target to lookup PCK summaries"
  {:malli/schema [:=> [:cat :map :some] [:map-of :int :map]]}
  [header buffer]
  (let [summaries (read-daf-summaries header (::forward header) buffer)
        segments  (mapv summary->pck-segment summaries)]
    (reduce (fn [lookup segment] (assoc lookup (::target segment) segment)) {} segments)))


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
    (.position ^ByteBuffer buffer ^long (* 8 ^long (- ^long (::end-i segment) 4)))
    (.get ^ByteBuffer buffer info)
    (convert-to-long (decode coefficient-layout-frame info) [::rsize ::n])))


(defn read-interval-coefficients
  "Read coefficient block with specified index from segment"
  [segment layout ^long index buffer]
  (let [component-count ({2 3 3 6} (::data-type segment))
        frame           (coefficient-frame (::rsize layout) component-count)
        data            (byte-array (sizeof frame))]
    (.position ^ByteBuffer buffer ^long (+ (* 8 ^long (dec ^long (::start-i segment))) ^long (* index ^long (sizeof frame))))
    (.get ^ByteBuffer buffer data)
    (reverse (apply map vector (drop 2 (decode frame data))))))


(defn chebyshev-polynomials
  "Chebyshev polynomials"
  [coefficients ^double s zero]
  (let [s2      (* 2.0 s)
        [w0 w1] (reduce (fn [[w0 w1] c] [(-> w0 (mult s2) (sub w1) (add c)) w0])
                        [zero zero]
                        (butlast coefficients))]
    (add (last coefficients) (sub (mult w0 s) w1))))


(defn interval-index-and-position
  "Compute interval index and position (s) inside for given timestamp"
  [layout ^double tdb]
  (let [init   (::init layout)
        intlen (::intlen layout)
        n      (::n layout)
        t      (- (* (- tdb ^double T0) ^double S-PER-DAY) ^double init)
        index  (min (max (long (quot t ^double intlen)) 0) ^long (dec ^long n))
        offset (- t (* ^long index ^double intlen))
        s      (- (/ (* 2.0 ^double offset) ^double intlen) 1.0)]
    [index s]))


(defn map-vec3
  "Convert list of vectors to list of vec3 objects"
  {:malli/schema [:=> [:cat [:sequential [:vector :double]]] [:sequential fvec3]]}
  [lst]
  (mapv #(apply vec3 %) lst))


(defn make-spk-document
  "Create object with SPK segment information"
  {:malli/schema [:=> [:cat :string] [:map [::header daf-header] [::lookup spk-lookup-table] [::buffer :some]]]}
  [filename]
  (let [buffer (map-file-to-buffer filename)
        header (read-daf-header buffer)
        cmt    (read-daf-comment header buffer)
        lookup (spk-segment-lookup-table header buffer)]
    (when (not (check-ftp-str header))
      (throw (RuntimeException. "FTPSTR has wrong value")))
    (when (not (check-endianness header))
      (throw (RuntimeException. "File endianness not implemented!")))
    {::header header
     ::comment cmt
     ::lookup lookup
     ::buffer buffer}))


(defn make-spk-segment-interpolator
  "Create object for interpolation of a particular target position"
  {:malli/schema [:=> [:cat [:map [::header daf-header] [::lookup spk-lookup-table] [::buffer :some]] :int :int] ifn?]}
  [spk center target]
  (let [buffer  (::buffer spk)
        lookup  (::lookup spk)
        segment (get lookup [center target])
        layout  (read-coefficient-layout segment buffer)
        cache   (z/lru (fn [index] (map-vec3 (read-interval-coefficients segment layout index buffer))) :lru/threshold 8)]
    (fn spk-segment-interpolate [tdb]
      (let [[index s]    (interval-index-and-position layout tdb)
            coefficients (cache index)]
        (chebyshev-polynomials coefficients s (vec3 0 0 0))))))


(defn make-pck-document
  "Create object with PCK segment information"
  {:malli/schema [:=> [:cat :string] [:map [::header daf-header] [::lookup pck-lookup-table] [::buffer :some]]]}
  [filename]
  (let [buffer (map-file-to-buffer filename)
        header (read-daf-header buffer)
        lookup (pck-segment-lookup-table header buffer)]
    (when (not (check-ftp-str header))
      (throw (RuntimeException. "FTPSTR has wrong value")))
    (when (not (check-endianness header))
      (throw (RuntimeException. "File endianness not implemented!")))
    {::header header
     ::lookup lookup
     ::buffer buffer}))


(defn make-pck-segment-interpolator
  "Create object for interpolation of a particular target position"
  {:malli/schema [:=> [:cat [:map [::header daf-header] [::lookup pck-lookup-table] [::buffer :some]] :int] ifn?]}
  [pck target]
  (let [buffer  (::buffer pck)
        lookup  (::lookup pck)
        segment (get lookup target)
        layout  (read-coefficient-layout segment buffer)
        cache   (z/lru (fn [index] (map-vec3 (read-interval-coefficients segment layout index buffer))) :lru/threshold 8)]
    (fn pck-segment-interpolate [tdb]
      (let [[index s]    (interval-index-and-position layout tdb)
            coefficients (cache index)]
        (chebyshev-polynomials coefficients s (vec3 0 0 0))))))


(def date (m/schema [:map [::year :int] [::month :int] [::day :int]]))


(defn julian-date
  "Convert calendar date to Julian date"
  ^long [{::keys [^long year ^long month ^long day]}]
  (let [g (- (+ year 4716) (if (<= month 2) 1 0))
        f (mod (+ month 9) 12)
        e (- (+ (quot (* 1461 ^long g) 4) day) 1402)
        J (+ e (quot (+ (* 153 ^long f) 2) 5))]
    (+ J (- 38 (quot (* (quot (+ ^long g 184) 100) 3) 4)))))


(defn calendar-date
  "Convert Julian date to calendar date"
  [^long jd]
  (let [f     (+ jd 1401)
        f     (+ ^long f (- (quot (* (quot (+ (* 4 jd) 274277) 146097) 3) 4) 38))
        e     (+ (* 4 ^long f) 3)
        g     (quot ^long (mod ^long e 1461) 4)
        h     (+ (* 5 ^long g) 2)
        day   (inc ^long (quot ^long (mod ^long h 153) 5))
        month (inc ^long (mod (+ (quot ^long h 153) 2) 12))
        year  (+ (- (quot ^long e 1461) 4716) (quot (- (+ 12 2) ^long month) 12))]
    {::year year ::month month ::day day}))


(def clock (m/schema [:map [::hour :int] [::minute :int] [::second :int]]))


(defn clock-time
  "Convert day fraction to hours, minutes, and seconds"
  [^double day-fraction]
  (let [hours   (* 24.0 day-fraction)
        hour    (int hours)
        minutes (* 60.0 (- hours hour))
        minute  (int minutes)
        seconds (* 60.0 (- minutes minute))
        sec     (int (+ seconds 0.5))]
    {::hour hour ::minute minute ::second sec}))


(defn psi-a
  "Compute Psi angle for Earth precession given centuries since 2000"
  ^double [^double t]
  (-> t (* -0.0000000951) (+ 0.000132851) (* t) (- 0.00114045) (* t) (- 1.0790069) (* t) (+ 5038.481507) (* t)))


(def eps0 84381.406)


(defn omega-a
  "Compute Omega angle for Earth precession given centuries since 2000"
  ^double [^double t]
  (-> t (* 0.0000003337) (- 0.000000467) (* t) (- 0.00772503) (* t) (+ 0.0512623) (* t) (- 0.025754) (* t) (+ ^double eps0)))


(defn chi-a
  "Compute Chi angle for Earth precession given centuries since 2000"
  ^double [^double t]
  (-> t (* -0.0000000560) (+ 0.000170663) (* t) (- 0.00121197) (* t) (- 2.3814292) (* t) (+ 10.556403) (* t)))


(defn compute-precession
  "Compute precession matrix for Earth given Julian day (see python-skyfield precessionlib.compute_precession)"
  ^Mat3x3 [^double tdb]
  (let [t          (/ (- tdb ^double T0) 36525.0)
        r3-chi-a   (rotation-matrix-3d-z (* (- (chi-a t)) ^double ASEC2RAD))
        r1-omega-a (rotation-matrix-3d-x (* (omega-a t) ^double ASEC2RAD))
        r3-psi-a   (rotation-matrix-3d-z (* (psi-a t) ^double ASEC2RAD))
        r1-eps0    (rotation-matrix-3d-x (* (- ^double eps0) ^double ASEC2RAD))]
    (mulm r3-chi-a (mulm r1-omega-a (mulm r3-psi-a r1-eps0)))))


(defn earth-rotation-angle
  "Compute Earth rotation angle as a value between 0 and 1 (see python-skyfield earthlib.earth_rotation_angle)"
  ^double [^double jd-ut]
  (let [th (+ 0.7790572732640 (* 0.00273781191135448 (- jd-ut ^double T0)))]
    (mod (+ ^double (mod th 1.0) ^double (mod jd-ut 1.0)) 1.0)))


(def earth-rotation-speed  ; Earth rotation speed in radians per second
  (/ (* 2.0 PI (+ 1.0 0.00273781191135448)) 86400))


(defn sidereal-time
  "Compute Greenwich Mean Sidereal Time (GMST) in hours"
  ^double [^double jd-ut]
  (let [theta (earth-rotation-angle jd-ut)
        t     (/ (- jd-ut ^double T0) 36525.0)
        st    (-> t (* -0.0000000368) (- 0.000029956)
                  (* t) (- 0.00000044)
                  (* t) (+ 1.3915817)
                  (* t) (+ 4612.156534)
                  (* t) (+ 0.014506))]
    (mod (+ (/ st 54000.0) (* theta 24.0)) 24.0)))


(defn- build_matrix
  "Construct conversion matrix from ICRS to J2000 (see python-skyfield.framelib.ICRS_to_J2000)"
  {:malli/schema [:=> :cat fmat3]}
  []
  (let [xi0  (* -0.0166170 ^double ASEC2RAD)
        eta0 (* -0.0068192 ^double ASEC2RAD)
        da0  (* -0.01460   ^double ASEC2RAD)
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


(defn icrs-to-now
  "Compute transformation matrix from ICRS to current epoch (omitting nutation)"
  {:malli/schema [:=> [:cat :double] fmat3]}
  [tdb]
  (mulm (compute-precession tdb) ICRS-to-J2000))


(def earth-to-icrs
  "Compute Earth orientation in ICRS coordinate system depending on time t (omitting nutation)"
  (z/lru
    (fn earth-to-icrs-fn [jd-ut]
      (mulm (inverse (icrs-to-now jd-ut)) (rotation-matrix-3d-z (* 2.0 PI (/ (sidereal-time jd-ut) 24.0)))))
    :lru/threshold 4))


(def pck-parser (insta/parser (slurp "resources/grammars/pck.bnf")))


(defn- do-assignment
  [environment [identifier operator value]]
  (case operator
    :=  (assoc environment identifier value)
    :+= (update environment identifier + value)))


(defn read-frame-kernel
  "Read frame specification kernel files and return hashmap with data"
  [filename]
  (let [string (slurp filename)]
    (transform
      {:START      (fn do-all-assignments [& assignments] (reduce do-assignment {} assignments))
       :ASSIGNMENT vector
       :STRING     identity
       :NUMBER     #(Integer/parseInt %)
       :DECIMAL    (comp #(Double/parseDouble %) #(clojure.string/replace % \D \E))
       :VECTOR     vector
       :EQUALS     (constantly :=)
       :PLUSEQUALS (constantly :+=)}
      (pck-parser string))))


(defn frame-kernel-body-frame-data
  "Get information about the specified body from the PCK data"
  [pck identifier]
  (let [number (pck identifier)]
    {::center (pck (format "FRAME_%d_CENTER" number))
     ::angles (pck (format "TKFRAME_%d_ANGLES" number))
     ::axes   (pck (format "TKFRAME_%d_AXES" number))
     ::units  (pck (format "TKFRAME_%d_UNITS" number))}))


(defn frame-kernel-body-frame
  "Convert body frame information to a transformation matrix"
  [{::keys [axes angles units]}]
  (let [scale     ({"DEGREES" DEGREES2RAD "ARCSECONDS" ASEC2RAD} units)
        rotations [rotation-matrix-3d-x rotation-matrix-3d-y rotation-matrix-3d-z]
        matrices  (mapv (fn [angle axis] ((rotations (dec ^long axis)) (* ^double angle ^double scale))) angles axes)]
    (reduce (fn [result rotation] (mulm rotation result)) (eye 3) matrices)))


(defn body-to-icrs
  "Create method to interpolate body positions (used for Moon at the moment)"
  [frame-kernel pck identifier target]
  (let [matrix       (frame-kernel-body-frame (frame-kernel-body-frame-data frame-kernel identifier))
        interpolator (make-pck-segment-interpolator pck target)]
    (fn interpolate-body-position [tdb]
      (let [components (interpolator tdb)
            ra         (.x ^Vec3 components)
            decl       (.y ^Vec3 components)
            w          (.z ^Vec3 components)
            rotation   (mulm matrix (mulm (rotation-matrix-3d-z (- w)) (mulm (rotation-matrix-3d-x (- decl)) (rotation-matrix-3d-z (- ra)))))]
        (transpose rotation)))))


(defn now
  "Get days since J2000 UTC"
  []
  (let [j2000-datetime (java.time.ZonedDateTime/of 2000 1 1 12 0 0 0 java.time.ZoneOffset/UTC)
        j2000-instant  (.toInstant j2000-datetime)
        t              (java.time.Instant/now)
        duration       (java.time.Duration/between j2000-instant t)]
    (/ (.toSeconds duration) ^double S-PER-DAY)))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
