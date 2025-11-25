#version 450 core

uniform float radius;

// Check whether a direction is pointing above the horizon depending on height of point.
bool is_above_horizon(vec3 point, vec3 direction)
{
  float dist = length(point);
  float sin_elevation_radius = dot(direction, point);
  float horizon_distance_sqr = dist * dist - radius * radius;
  return sin_elevation_radius >= 0 || sin_elevation_radius * sin_elevation_radius <= horizon_distance_sqr;
}
