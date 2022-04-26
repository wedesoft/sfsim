#version 410 core

// Determine angle of horizon depending on height of point.
float horizon_angle(vec3 point, float radius)
{
  float dist = length(point);
  return acos(min(radius / dist, 1));
}
