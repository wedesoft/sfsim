#version 410 core

in GEO_OUT
{
  vec2 colorcoord;
  vec3 point;
} fs_in;

out vec4 fragColor;

vec4 cloud_point(vec3 point);

void main()
{
  fragColor = cloud_point(fs_in.point);
}
