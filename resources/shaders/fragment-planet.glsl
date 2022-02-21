#version 410 core
uniform sampler2D colors;
in mediump vec2 colorcoord_frag;
out lowp vec3 fragColor;
void main()
{
  fragColor = texture(colors, colorcoord_frag).rgb;
}
