# TODO
* downsample cube globe to get coarse levels (color map, displacement map, normal map, water map)
* Get scale-image to work on large images
* compute 3D coordinates from elevation map and cube coordinates
* compute cube globe including deviation from sphere

* OpenGL draw object: GL30/glBindVertexArray, GL20/glEnableVertexAttribArray, GL11/glDrawElements, disable vertex attrib array, bind to zero
* delete OpenGL object: GL30/glBindVertexArray, delete vertex buffer, delete index buffer, delete vertex array, bind to zero
* bind render config, bind shader, update uniforms (world matrix, projection matrix)
* Skydome: counter-clockwise front face (GL11/glFrontFace GL11/GL\_CCW) (configuration object)
* Skydome scaled to ZFAR * 0.5
* render quad-tree recursively, boolean "isleaf"
* (GL40/glPatchParameteri GL40/GL\_PATCH\_VERTICES size) (uses vertex array)
* (GL11/glDrawArrays GL40/GL\_PATCHES, 0, size
* always add child nodes or remove child nodes?
