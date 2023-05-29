(import '[org.lwjgl BufferUtils]
        '[org.lwjgl.glfw GLFW]
        '[org.lwjgl.stb STBImage]
        '[org.lwjgl.opengl GL GL11 GL12 GL13 GL15 GL20 GL30 GL32 GL45])

(defn load-image [path]
  (let [width (int-array 1)
        height (int-array 1)
        channels (int-array 1)]
    (with-open [stream (io/input-stream (io/resource path))]
      (let [image (STBImage/stbi_load_from_memory (.toByteArray stream) width height channels 0)]
        {:data image
         :width (aget width 0)
         :height (aget height 0)
         :channels (aget channels 0)}))))
