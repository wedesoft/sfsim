#version 410 core

float horizon_angle(vec3 point, float radius);
float elevation_to_index(int size, float elevation, float horizon_angle, float power, bool sky, bool ground);

vec2 transmittance_forward(vec3 point, vec3 direction, float radius, float max_height, int size, float power, bool sky)
{
  float dist = length(point);
  float height = dist - radius;
  vec3 normal = point / dist;
  float elevation = acos(dot(normal, direction));
  float horizon = horizon_angle(point, radius);
  float elevation_index = elevation_to_index(size, elevation, horizon, power, sky, false);
  return vec2(elevation_index, height / max_height);
}
