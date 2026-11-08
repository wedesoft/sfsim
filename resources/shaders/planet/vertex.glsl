#version 450 core

in vec2 surfacecoord;
in vec2 colorcoord;

out VS_OUT
{
  vec2 surfacecoord;
  vec2 colorcoord;
} vs_out;

// Vertex shader to pass through coordinates and texture indices for surface pointcloud and color texture.
void main()
{
  vs_out.surfacecoord = surfacecoord;
  vs_out.colorcoord = colorcoord;
}
