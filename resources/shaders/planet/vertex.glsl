#version 410 core

in vec3 point;
in vec2 heightcoord;
in vec2 colorcoord;

out VS_OUT
{
  vec2 heightcoord;
  vec2 colorcoord;
} vs_out;

// Vertex shader to pass through coordinates and texture indices for surface pointcloud and color texture.
void main()
{
  gl_Position = vec4(point, 1);
  vs_out.heightcoord = heightcoord;
  vs_out.colorcoord = colorcoord;
}
