#version 410 core
in GEO_OUT
{
  mediump vec2 colorcoord;
} fs_in;
out lowp vec3 fragColor;
void main()
{
  fragColor = vec3(1, 1, 1);
}
