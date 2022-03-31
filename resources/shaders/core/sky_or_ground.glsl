#version 410 core

float horizon_angle(vec3 point, float radius);

bool sky_or_ground(float radius, vec3 point, vec3 direction)
{
  float horizon = horizon_angle(point, radius);
  vec3 normal = normalize(point);
  float cos_elevation = dot(normal, direction);
  return cos_elevation >= -sin(horizon); // returns true for sky, false for ground
}
