#version 410 core

float horizon_angle(vec3 point, float radius);

// Check whether a direction is pointing above the horizon depending on height of point.
bool is_above_horizon(float radius, vec3 point, vec3 direction)
{
  float horizon = horizon_angle(point, radius);
  vec3 normal = normalize(point);
  float cos_elevation = dot(normal, direction);
  return cos_elevation >= -sin(horizon); // returns true for sky, false for ground
}
