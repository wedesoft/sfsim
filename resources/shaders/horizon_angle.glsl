#version 410 core

float horizon_angle(vec3 point, float radius)
{
  float dist = length(point);
  return acos(min(radius / dist, 1));
}
