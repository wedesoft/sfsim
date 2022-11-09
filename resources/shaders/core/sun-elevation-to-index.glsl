#version 410 core

float sun_elevation_to_index(vec3 point, vec3 light_direction)
{
  float sin_elevation = dot(point, light_direction) / length(point);
  return max(0, (1 - exp(- 3 * sin_elevation - 0.6)) / (1 - exp(-3.6)));
}
