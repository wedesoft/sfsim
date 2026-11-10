#version 450 core

uniform int height_tilesize;
uniform int color_tilesize;

out VS_OUT
{
  vec2 surfacecoord;
  vec2 colorcoord;
} vs_out;

// Vertex shader to pass through coordinates and texture indices for surface pointcloud and color texture.
void main()
{
  vec2 corner = vec2(gl_VertexID & 1, (gl_VertexID >> 1) & 1);
  float h = 0.5 / height_tilesize;
  float c = 0.5 / color_tilesize;
  vs_out.surfacecoord = vec2(h) + corner * (1.0 - 2.0 * h);
  vs_out.colorcoord   = vec2(c) + corner * (1.0 - 2.0 * c);
}
