#version 410 core

in GEO_OUT
{
  vec2 colorcoord;
  vec3 point;
} fs_in;

out vec4 fragColor;

vec4 cloud_planet(vec3 point);

void main()
{
  fragColor = cloud_planet(fs_in.point);
}
