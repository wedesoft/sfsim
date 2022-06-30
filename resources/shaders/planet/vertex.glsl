#version 410 core

in highp vec3 point;
in mediump vec2 heightcoord;
in mediump vec2 colorcoord;

out VS_OUT
{
  mediump vec2 heightcoord;
  mediump vec2 colorcoord;
} vs_out;

// Vertex shader to pass through coordinates and texture indices for heightfield and texture.
void main()
{
  gl_Position = vec4(point, 1);
  vs_out.heightcoord = heightcoord;
  vs_out.colorcoord = colorcoord;
}
