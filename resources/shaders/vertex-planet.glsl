#version 410 core
in highp vec3 point;
in mediump vec2 heightcoord;
in mediump vec2 colorcoord;
void main()
{
  gl_Position = vec4(point, 1);
}
