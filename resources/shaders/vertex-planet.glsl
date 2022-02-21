#version 410 core
in highp vec3 point;
in mediump vec2 heightcoord;
in mediump vec2 colorcoord;
out mediump vec2 colorcoord_tcs;
void main()
{
  gl_Position = vec4(point, 1);
  colorcoord_tcs = colorcoord;
}
