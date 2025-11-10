#version 410 core

uniform vec3 origin;

in GEO_OUT
{
  vec2 colorcoord;
  vec3 point;
} fs_in;

out vec4 fragColor;

vec4 cloud_point(vec3 origin, vec3 direction, vec3 point);

void main()
{
  fragColor = cloud_point(origin, normalize(fs_in.point - origin), fs_in.point);  //.TODO: get more accurate direction
}
