;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.texture
  (:require
    [malli.core :as m]
    [sfsim.image :refer (image byte-image float-image-2d float-image-3d float-image-4d)]
    [sfsim.util :refer (N)])
  (:import
    (org.lwjgl
      BufferUtils)
    (org.lwjgl.opengl
      GL11
      GL12
      GL13
      GL14
      GL30
      GL31
      GL42)))


(set! *unchecked-math* :warn-on-boxed)
(set! *warn-on-reflection* true)

(def texture (m/schema [:map [::target :int] [::texture :int]]))
(def texture-1d (m/schema [:map [::width N] [::target :int] [::texture :int]]))
(def texture-2d (m/schema [:map [::width N] [::height N] [::target :int] [::texture :int]]))
(def texture-3d (m/schema [:map [::width N] [::height N] [::depth N] [::target :int] [::texture :int]]))
(def texture-4d (m/schema [:map [::width N] [::height N] [::depth N] [::hyperdepth N] [::target :int] [::texture :int]]))


(defn make-float-buffer
  "Create a floating-point buffer object"
  {:malli/schema [:=> [:cat seqable?] :some]}
  [data]
  (doto (BufferUtils/createFloatBuffer (count data))
    (.put ^floats data)
    (.flip)))


(defn make-int-buffer
  "Create a integer buffer object"
  {:malli/schema [:=> [:cat seqable?] :some]}
  [data]
  (doto (BufferUtils/createIntBuffer (count data))
    (.put ^ints data)
    (.flip)))


(defn make-byte-buffer
  "Create a byte buffer object"
  {:malli/schema [:=> [:cat bytes?] :some]}
  [data]
  (doto (BufferUtils/createByteBuffer (count data))
    (.put ^bytes data)
    (.flip)))


(defmacro with-texture
  "Macro to bind a texture and open a context with it"
  [target texture & body]
  `(do
     (GL11/glBindTexture ~target ~texture)
     (let [result# (do ~@body)]
       (GL11/glBindTexture ~target 0)
       result#)))


(defmacro create-texture
  "Macro to create a texture and open a context with it"
  [target texture & body]
  `(let [~texture (GL11/glGenTextures)]
     (with-texture ~target ~texture ~@body)))


(defn generate-mipmap
  "Generate mipmap for texture and set texture min filter to linear mipmap mode"
  {:malli/schema [:=> [:cat texture] texture]}
  [texture]
  (let [target (::target texture)]
    (with-texture target (::texture texture)
      (GL11/glTexParameteri target GL11/GL_TEXTURE_MIN_FILTER GL11/GL_LINEAR_MIPMAP_LINEAR)
      (GL30/glGenerateMipmap target))
    texture))


(def interpolation (m/schema [:enum ::nearest ::linear]))

(defmulti setup-interpolation
  "Initialize different types of texture interpolation"
  (fn [_target interpolation] interpolation))
(m/=> setup-interpolation [:=> [:cat :int interpolation] :nil])


(defmethod setup-interpolation ::nearest
  [target _interpolation]
  (GL11/glTexParameteri target GL11/GL_TEXTURE_MIN_FILTER GL11/GL_NEAREST)
  (GL11/glTexParameteri target GL11/GL_TEXTURE_MAG_FILTER GL11/GL_NEAREST))


(defmethod setup-interpolation ::linear
  [target _interpolation]
  (GL11/glTexParameteri target GL11/GL_TEXTURE_MIN_FILTER GL11/GL_LINEAR)
  (GL11/glTexParameteri target GL11/GL_TEXTURE_MAG_FILTER GL11/GL_LINEAR))


(def boundary (m/schema [:enum ::clamp ::repeat]))

(defmulti setup-boundary-1d
  "Configure different types of boundary threatment for 1D texture"
  identity)
(m/=> setup-boundary-1d [:=> [:cat boundary] :nil])


(defmethod setup-boundary-1d ::clamp
  [_boundary]
  (GL11/glTexParameteri GL11/GL_TEXTURE_1D GL11/GL_TEXTURE_WRAP_S GL12/GL_CLAMP_TO_EDGE))


(defmethod setup-boundary-1d ::repeat
  [_boundary]
  (GL11/glTexParameteri GL11/GL_TEXTURE_1D GL11/GL_TEXTURE_WRAP_S GL11/GL_REPEAT))


(defmacro create-texture-1d
  "Macro to initialise 1D texture"
  [interpolation boundary width & body]
  `(create-texture GL11/GL_TEXTURE_1D texture#
                   (setup-interpolation GL11/GL_TEXTURE_1D ~interpolation)
                   (setup-boundary-1d ~boundary)
                   ~@body
                   {::texture texture# ::target GL11/GL_TEXTURE_1D ::width ~width}))


(defmulti setup-boundary-2d
  "Configure different types of boundary threatment for 2D texture"
  identity)
(m/=> setup-boundary-2d [:=> [:cat boundary] :nil])


(defmethod setup-boundary-2d ::clamp
  [_boundary]
  (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_WRAP_S GL12/GL_CLAMP_TO_EDGE)
  (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_WRAP_T GL12/GL_CLAMP_TO_EDGE))


(defmethod setup-boundary-2d ::repeat
  [_boundary]
  (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_WRAP_S GL11/GL_REPEAT)
  (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_WRAP_T GL11/GL_REPEAT))


(defmacro create-texture-2d
  "Macro to initialise 2D texture"
  [interpolation boundary width height & body]
  `(create-texture GL11/GL_TEXTURE_2D texture#
                   (setup-interpolation GL11/GL_TEXTURE_2D ~interpolation)
                   (setup-boundary-2d ~boundary)
                   ~@body
                   {::texture texture# ::target GL11/GL_TEXTURE_2D ::width ~width ::height ~height}))


(defmacro create-depth-texture
  "Macro to initialise shadow map"
  [interpolation boundary width height & body]
  `(create-texture-2d ~interpolation ~boundary ~width ~height
                      (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL14/GL_TEXTURE_COMPARE_MODE GL14/GL_COMPARE_R_TO_TEXTURE)
                      (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL14/GL_TEXTURE_COMPARE_FUNC GL11/GL_GEQUAL)
                      ~@body))


(defmulti setup-boundary-3d
  "Configure different types of boundary threatment for 3D texture"
  (fn [_target boundary] boundary))
(m/=> setup-boundary-3d [:=> [:cat :int boundary] :nil])


(defmethod setup-boundary-3d ::clamp
  [target _boundary]
  (GL11/glTexParameteri target GL11/GL_TEXTURE_WRAP_S GL12/GL_CLAMP_TO_EDGE)
  (GL11/glTexParameteri target GL11/GL_TEXTURE_WRAP_T GL12/GL_CLAMP_TO_EDGE)
  (GL11/glTexParameteri target GL12/GL_TEXTURE_WRAP_R GL12/GL_CLAMP_TO_EDGE))


(defmethod setup-boundary-3d ::repeat
  [target _boundary]
  (GL11/glTexParameteri target GL11/GL_TEXTURE_WRAP_S GL11/GL_REPEAT)
  (GL11/glTexParameteri target GL11/GL_TEXTURE_WRAP_T GL11/GL_REPEAT)
  (GL11/glTexParameteri target GL12/GL_TEXTURE_WRAP_R GL11/GL_REPEAT))


(defmacro create-texture-3d
  "Macro to initialise 3D texture"
  [interpolation boundary width height depth & body]
  `(create-texture GL12/GL_TEXTURE_3D texture#
                   (setup-interpolation GL12/GL_TEXTURE_3D ~interpolation)
                   (setup-boundary-3d GL12/GL_TEXTURE_3D ~boundary)
                   ~@body
                   {::texture texture# ::target GL12/GL_TEXTURE_3D ::width ~width ::height ~height ::depth ~depth}))


(defn make-float-texture-1d
  "Load floating-point 1D data into red channel of an OpenGL texture"
  {:malli/schema [:=> [:cat interpolation boundary seqable?] texture-1d]}
  [interpolation boundary data]
  (let [buffer (make-float-buffer data)
        width  (count data)]
    (create-texture-1d interpolation boundary width
                       (GL11/glTexImage1D GL11/GL_TEXTURE_1D 0 GL30/GL_R32F width 0 GL11/GL_RED GL11/GL_FLOAT
                                          ^java.nio.DirectFloatBufferU buffer))))


(defn- make-byte-texture-2d-base
  "Initialise a 2D byte texture"
  {:malli/schema [:=> [:cat image interpolation boundary :int :int :int] texture-2d]}
  [image interpolation boundary internalformat format_ type_]
  (let [buffer (make-byte-buffer (:sfsim.image/data image))]
    (create-texture-2d interpolation boundary (:sfsim.image/width image) (:sfsim.image/height image)
                       (GL11/glTexImage2D GL11/GL_TEXTURE_2D 0 ^long internalformat ^long (:sfsim.image/width image)
                                          ^long (:sfsim.image/height image) 0 ^long format_ ^long type_ ^java.nio.DirectByteBuffer buffer))))


(defn- make-float-texture-2d-base
  "Initialise a 2D texture"
  {:malli/schema [:=> [:cat image interpolation boundary :int :int :int] texture-2d]}
  [image interpolation boundary internalformat format_ type_]
  (let [buffer (make-float-buffer (:sfsim.image/data image))]
    (create-texture-2d interpolation boundary (:sfsim.image/width image) (:sfsim.image/height image)
                       (GL11/glTexImage2D GL11/GL_TEXTURE_2D 0 ^long internalformat ^long (:sfsim.image/width image)
                                          ^long (:sfsim.image/height image) 0 ^long format_ ^long type_ ^java.nio.DirectFloatBufferU buffer))))


(defn make-rgb-texture
  "Load image into an RGB OpenGL texture"
  {:malli/schema [:=> [:cat interpolation boundary image] texture-2d]}
  [interpolation boundary image]
  (make-byte-texture-2d-base image interpolation boundary GL11/GL_RGB GL12/GL_RGBA GL11/GL_UNSIGNED_BYTE))


(defn make-rgba-texture
  "Load image into an RGBA OpenGL texture"
  {:malli/schema [:=> [:cat interpolation boundary image] texture-2d]}
  [interpolation boundary image]
  (make-byte-texture-2d-base image interpolation boundary GL11/GL_RGBA GL12/GL_RGBA GL11/GL_UNSIGNED_BYTE))


(defn make-depth-texture
  "Load floating-point values into a shadow map"
  {:malli/schema [:=> [:cat interpolation boundary float-image-2d] texture-2d]}
  [interpolation boundary image]
  (assoc (create-depth-texture interpolation boundary (:sfsim.image/width image) (:sfsim.image/height image)
                               (GL11/glTexImage2D GL11/GL_TEXTURE_2D 0 GL30/GL_DEPTH_COMPONENT32F
                                                  ^long (:sfsim.image/width image) ^long (:sfsim.image/height image) 0
                                                  GL11/GL_DEPTH_COMPONENT GL11/GL_FLOAT
                                                  ^java.nio.DirectFloatBufferU (make-float-buffer (:sfsim.image/data image))))
         :stencil false))


(defn make-empty-texture-2d
  "Create 2D texture with specified format and allocate storage"
  {:malli/schema [:=> [:cat interpolation boundary :int N N] texture-2d]}
  [interpolation boundary internalformat width height]
  (create-texture-2d interpolation boundary width height
                     (GL42/glTexStorage2D GL11/GL_TEXTURE_2D 1 internalformat width height)))


(defn make-empty-float-texture-2d
  "Create 2D floating-point texture and allocate storage"
  {:malli/schema [:=> [:cat interpolation boundary N N] texture-2d]}
  [interpolation boundary width height]
  (make-empty-texture-2d interpolation boundary GL30/GL_R32F width height))


(defn make-empty-depth-texture-2d
  "Create 2D depth texture and allocate storage"
  {:malli/schema [:=> [:cat interpolation boundary N N] texture-2d]}
  [interpolation boundary width height]
  (assoc (create-depth-texture interpolation boundary width height
                               (GL42/glTexStorage2D GL11/GL_TEXTURE_2D 1 GL30/GL_DEPTH_COMPONENT32F width height))
         :stencil false))


(defn make-empty-depth-stencil-texture-2d
  "Create 2D depth texture and allocate storage"
  {:malli/schema [:=> [:cat interpolation boundary N N] texture-2d]}
  [interpolation boundary width height]
  (assoc (create-depth-texture interpolation boundary width height
                               (GL42/glTexStorage2D GL11/GL_TEXTURE_2D 1 GL30/GL_DEPTH32F_STENCIL8 width height))
         :stencil true))


(defn make-float-texture-2d
  "Load floating-point 2D data into red channel of an OpenGL texture"
  {:malli/schema [:=> [:cat interpolation boundary float-image-2d] texture-2d]}
  [interpolation boundary image]
  (make-float-texture-2d-base image interpolation boundary GL30/GL_R32F GL11/GL_RED GL11/GL_FLOAT))


(defn make-ubyte-texture-2d
  "Load unsigned-byte 2D data into red channel of an OpenGL texture (data needs to be 32-bit aligned!)"
  {:malli/schema [:=> [:cat interpolation boundary byte-image] texture-2d]}
  [interpolation boundary image]
  (make-byte-texture-2d-base image interpolation boundary GL11/GL_RED GL11/GL_RED GL11/GL_UNSIGNED_BYTE))


(defmacro create-texture-2d-array
  "Macro to initialise cubemap"
  [interpolation boundary width height depth & body]
  `(create-texture GL30/GL_TEXTURE_2D_ARRAY texture#
                   (setup-interpolation GL30/GL_TEXTURE_2D_ARRAY ~interpolation)
                   (setup-boundary-3d GL30/GL_TEXTURE_2D_ARRAY ~boundary)
                   ~@body
                   {::width ~width ::height ~height ::depth ~depth ::target GL30/GL_TEXTURE_2D_ARRAY ::texture texture#}))


(defn- make-byte-texture-2d-array
  "Initialise a 2D byte texture"
  {:malli/schema [:=> [:cat [:vector image] interpolation boundary :int :int :int] texture-3d]}
  [images interpolation boundary internalformat format_ type_]
  (let [width  (:sfsim.image/width (first images))
        height (:sfsim.image/height (first images))
        depth  (count images)
        size   (* depth (count (:sfsim.image/data (first images))))]
    (create-texture-2d-array interpolation boundary width height depth
                             (GL12/glTexImage3D GL30/GL_TEXTURE_2D_ARRAY 0 ^long internalformat ^long width ^long height depth 0 ^long format_
                                                ^long type_ ^java.nio.DirectByteBuffer (java.nio.ByteBuffer/allocateDirect size))
                             (doseq [[index image] (map-indexed vector images)]
                               (let [buffer (make-byte-buffer (:sfsim.image/data image))]
                                 (GL31/glTexSubImage3D GL30/GL_TEXTURE_2D_ARRAY 0 0 0 ^long index ^long width ^long height 1 ^long format_
                                                       ^long type_ ^java.nio.DirectByteBuffer buffer))))))


(defn make-rgb-texture-array
  "Create 2D RGB texture array"
  [interpolation boundary images]
  (make-byte-texture-2d-array images interpolation boundary GL11/GL_RGB GL12/GL_RGBA GL11/GL_UNSIGNED_BYTE))


(defn make-vector-texture-2d
  "Load floating point 2D array of 3D vectors into OpenGL texture"
  {:malli/schema [:=> [:cat interpolation boundary float-image-2d] texture-2d]}
  [interpolation boundary image]
  (make-float-texture-2d-base image interpolation boundary GL30/GL_RGB32F GL12/GL_RGB GL11/GL_FLOAT))


(defn make-float-texture-3d
  "Load floating-point 3D data into red channel of an OpenGL texture"
  {:malli/schema [:=> [:cat interpolation boundary float-image-3d] texture-3d]}
  [interpolation boundary image]
  (let [buffer (make-float-buffer (:sfsim.image/data image))]
    (create-texture-3d interpolation boundary (:sfsim.image/width image) (:sfsim.image/height image) (:sfsim.image/depth image)
                       (GL12/glTexImage3D GL12/GL_TEXTURE_3D 0 GL30/GL_R32F ^long (:sfsim.image/width image) ^long (:sfsim.image/height image)
                                          ^long (:sfsim.image/depth image) 0 GL11/GL_RED GL11/GL_FLOAT ^java.nio.DirectFloatBufferU buffer))))


(defn make-empty-float-texture-3d
  "Create empty 3D floating-point texture"
  {:malli/schema [:=> [:cat interpolation boundary N N N] texture-3d]}
  [interpolation boundary width height depth]
  (create-texture-3d interpolation boundary width height depth
                     (GL42/glTexStorage3D GL12/GL_TEXTURE_3D 1 GL30/GL_R32F width height depth)))


(defn- make-float-texture-4d
  "Initialise a 2D texture representing a 4D array"
  {:malli/schema [:=> [:cat float-image-4d interpolation boundary :int :int :int] texture-4d]}
  [image interpolation boundary internalformat format_ type_]
  (let [buffer     (make-float-buffer (:sfsim.image/data image))
        width      (:sfsim.image/width image)
        height     (:sfsim.image/height image)
        depth      (:sfsim.image/depth image)
        hyperdepth (:sfsim.image/hyperdepth image)
        width-2d   (* ^long width ^long depth)
        height-2d  (* ^long height ^long hyperdepth)]
    (assoc (create-texture-2d interpolation boundary width-2d height-2d
                              (GL11/glTexImage2D GL11/GL_TEXTURE_2D 0 ^long internalformat ^long width-2d ^long height-2d 0
                                                 ^long format_ ^long type_ ^java.nio.DirectFloatBufferU buffer))
           ::width width
           ::height height
           ::depth depth
           ::hyperdepth hyperdepth)))


(defn make-vector-texture-4d
  "Load floating point 2D array of 3D vectors into OpenGL texture"
  {:malli/schema [:=> [:cat interpolation boundary float-image-4d] texture-4d]}
  [interpolation boundary image]
  (make-float-texture-4d image interpolation boundary GL30/GL_RGB32F GL12/GL_RGB GL11/GL_FLOAT))


(defn destroy-texture
  "Delete an OpenGL texture"
  {:malli/schema [:=> [:cat texture] :nil]}
  [texture]
  (GL11/glDeleteTextures ^long (::texture texture)))


(defn byte-buffer->array
  "Convert byte buffer to byte array"
  {:malli/schema [:=> [:cat :some] bytes?]}
  [buffer]
  (let [result (byte-array (.limit ^java.nio.DirectByteBuffer buffer))]
    (.get ^java.nio.DirectByteBuffer buffer result)
    (.flip ^java.nio.DirectByteBuffer buffer)
    result))


(defn float-buffer->array
  "Convert float buffer to flaot array"
  {:malli/schema [:=> [:cat :some] seqable?]}
  [buffer]
  (let [result (float-array (.limit ^java.nio.DirectFloatBufferU buffer))]
    (.get ^java.nio.DirectFloatBufferU buffer result)
    (.flip ^java.nio.DirectFloatBufferU buffer)
    result))


(defn depth-texture->floats
  "Extract floating-point depth map from texture"
  {:malli/schema [:=> [:cat texture-2d] float-image-2d]}
  [{::keys [^long target ^long texture ^long width ^long height]}]
  (with-texture target texture
    (let [buf (BufferUtils/createFloatBuffer (* width height))]
      (GL11/glGetTexImage GL11/GL_TEXTURE_2D 0 GL11/GL_DEPTH_COMPONENT GL11/GL_FLOAT buf)
      #:sfsim.image{:width width :height height :data (float-buffer->array buf)})))


(defn float-texture-2d->floats
  "Extract floating-point floating-point data from texture"
  {:malli/schema [:=> [:cat texture-2d] float-image-2d]}
  [{::keys [^long target ^long texture ^long width ^long height]}]
  (with-texture target texture
    (let [buf (BufferUtils/createFloatBuffer (* width height))]
      (GL11/glGetTexImage GL11/GL_TEXTURE_2D 0 GL11/GL_RED GL11/GL_FLOAT buf)
      #:sfsim.image{:width width :height height :data (float-buffer->array buf)})))


(defn float-texture-3d->floats
  "Extract floating-point floating-point data from texture"
  {:malli/schema [:=> [:cat texture-3d] float-image-3d]}
  [{::keys [^long target ^long texture ^long width ^long height ^long depth]}]
  (with-texture target texture
    (let [buf (BufferUtils/createFloatBuffer (* width height depth))]
      (GL11/glGetTexImage GL12/GL_TEXTURE_3D 0 GL11/GL_RED GL11/GL_FLOAT buf)
      #:sfsim.image{:width width :height height :depth depth :data (float-buffer->array buf)})))


(defn rgb-texture->vectors3
  "Extract floating-point RGB vectors from texture"
  {:malli/schema [:=> [:cat texture-2d] float-image-2d]}
  [{::keys [^long target ^long texture ^long width ^long height]}]
  (with-texture target texture
    (let [buf (BufferUtils/createFloatBuffer (* width height 3))]
      (GL11/glGetTexImage GL11/GL_TEXTURE_2D 0 GL12/GL_RGB GL11/GL_FLOAT buf)
      #:sfsim.image{:width width :height height :data (float-buffer->array buf)})))


(defn rgba-texture->vectors4
  "Extract floating-point RGBA vectors from texture"
  {:malli/schema [:=> [:cat texture-2d] float-image-2d]}
  [{::keys [^long target ^long texture ^long width ^long height]}]
  (with-texture target texture
    (let [buf  (BufferUtils/createFloatBuffer (* width height 4))]
      (GL11/glGetTexImage GL11/GL_TEXTURE_2D 0 GL12/GL_RGBA GL11/GL_FLOAT buf)
      #:sfsim.image{:width width :height height :data (float-buffer->array buf)})))


(defn texture->image
  "Convert texture to RGB image"
  {:malli/schema [:=> [:cat texture-2d] image]}
  [{::keys [^long target ^long texture ^long width ^long height]}]
  (with-texture target texture
    (let [size (* 4 width height)
          buf  (BufferUtils/createByteBuffer size)]
      (GL11/glGetTexImage GL11/GL_TEXTURE_2D 0 GL12/GL_RGBA GL11/GL_UNSIGNED_BYTE buf)
      #:sfsim.image{:width width :height height :data (byte-buffer->array buf) :channels 4})))


(defmacro create-cubemap
  "Macro to initialise cubemap"
  [interpolation boundary size & body]
  `(create-texture GL13/GL_TEXTURE_CUBE_MAP texture#
                   (setup-interpolation GL13/GL_TEXTURE_CUBE_MAP ~interpolation)
                   (setup-boundary-3d GL13/GL_TEXTURE_CUBE_MAP ~boundary)
                   ~@body
                   {::width ~size ::height ~size ::depth 6 ::target GL13/GL_TEXTURE_CUBE_MAP ::texture texture#}))


(defn- make-cubemap
  "Initialise a cubemap"
  {:malli/schema [:=> [:cat [:vector float-image-2d] interpolation boundary :int :int :int] texture-3d]}
  [images interpolation boundary internalformat format_ type_]
  (let [size (:sfsim.image/width (first images))]
    (create-cubemap interpolation boundary size
                    (doseq [[^long face image] (map-indexed vector images)]
                      (GL11/glTexImage2D ^long (+ GL13/GL_TEXTURE_CUBE_MAP_POSITIVE_X face) 0 ^long internalformat
                                         ^long size ^long size 0 ^long format_ ^long type_
                                         ^java.nio.DirectFloatBufferU (make-float-buffer (:sfsim.image/data image)))))))


(defn make-float-cubemap
  "Load floating-point 2D textures into red channel of an OpenGL cubemap"
  {:malli/schema [:=> [:cat interpolation boundary [:sequential float-image-2d]] texture-3d]}
  [interpolation boundary images]
  (make-cubemap images interpolation boundary GL30/GL_R32F GL11/GL_RED GL11/GL_FLOAT))


(defn make-empty-float-cubemap
  "Create empty cubemap with faces of specified size"
  {:malli/schema [:=> [:cat interpolation boundary :int] texture-3d]}
  [interpolation boundary size]
  (create-cubemap interpolation boundary size
                  (GL42/glTexStorage2D GL13/GL_TEXTURE_CUBE_MAP 1 GL30/GL_R32F size size)))


(defn float-cubemap->floats
  "Extract floating-point data from cubemap face"
  [{::keys [^long target ^long texture ^long width ^long height]} ^long face]
  (with-texture target texture
    (let [buf (BufferUtils/createFloatBuffer (* width height))]
      (GL11/glGetTexImage ^long (+ GL13/GL_TEXTURE_CUBE_MAP_POSITIVE_X face) 0 GL11/GL_RED GL11/GL_FLOAT buf)
      #:sfsim.image{:width width :height height :data (float-buffer->array buf)})))


(defn make-vector-cubemap
  "Load vector 2D textures into an OpenGL cubemap"
  {:malli/schema [:=> [:cat interpolation boundary [:vector float-image-2d]] texture-3d]}
  [interpolation boundary images]
  (make-cubemap images interpolation boundary GL30/GL_RGB32F GL12/GL_RGB GL11/GL_FLOAT))


(defn make-empty-vector-cubemap
  "Create empty cubemap with faces of specified size"
  {:malli/schema [:=> [:cat interpolation boundary :int] texture-3d]}
  [interpolation boundary size]
  (create-cubemap interpolation boundary size
                  (GL42/glTexStorage2D GL13/GL_TEXTURE_CUBE_MAP 1 GL30/GL_RGB32F size size)))


(defn vector-cubemap->vectors3
  "Extract floating-point vector data from cubemap face"
  [{::keys [^long target ^long texture ^long width ^long height]} ^long face]
  (with-texture target texture
    (let [buf (BufferUtils/createFloatBuffer (* width height 3))]
      (GL11/glGetTexImage ^long (+ GL13/GL_TEXTURE_CUBE_MAP_POSITIVE_X face) 0 GL12/GL_RGB GL11/GL_FLOAT buf)
      #:sfsim.image{:width width :height height :data (float-buffer->array buf)})))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
