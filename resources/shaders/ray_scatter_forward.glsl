#version 410 core

float horizon_angle(vec3 point, float radius);
float elevation_to_index(int size, float elevation, float horizon_angle, float power);

vec4 ray_scatter_forward(vec3 point, vec3 direction, vec3 light_direction, float radius, float max_height, int size, float power)
{
  float dist = length(point);
  vec3 normal = point / dist;
  float horizon = horizon_angle(point, radius);
  float cos_elevation = dot(normal, direction);
  float elevation = acos(cos_elevation);
  float elevation_index = elevation_to_index(size, elevation, horizon, power);
  float height = dist - radius;
  float height_index = height / max_height;
  return vec4(0, 0, elevation_index, height_index);
}
