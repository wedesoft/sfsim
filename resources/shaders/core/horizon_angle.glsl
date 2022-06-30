#version 410 core

uniform float radius;

// Determine angle of horizon depending on height of point.
float horizon_angle(vec3 point)
{
  float dist = length(point);
  return acos(min(radius / dist, 1));
}
