#version 450 core

uniform vec3 origin;
uniform vec3 object_origin;

in GEO_OUT
{
  vec2 colorcoord;
  vec3 point;
  vec3 object_point;
  vec4 camera_point;
} fs_in;

out vec4 fragColor;

vec4 cloud_plume_cloud_point(vec3 origin, vec3 direction, vec3 object_origin, vec3 object_direction, float dist);

void main()
{
  vec3 object_direction = normalize(fs_in.object_point - object_origin);
  float dist = distance(origin, fs_in.point);
  //.TODO: get more accurate direction
  fragColor = cloud_plume_cloud_point(origin, normalize(fs_in.point - origin), object_origin, object_direction, dist);
}
