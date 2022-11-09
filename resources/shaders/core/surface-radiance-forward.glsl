#version 410 core

float height_to_index(vec3 point);
float sun_elevation_to_index(vec3 point, vec3 light_direction);

vec2 surface_radiance_forward(vec3 point, vec3 light_direction)
{
  float height_index = height_to_index(point);
  float sun_elevation_index = sun_elevation_to_index(point, light_direction);
  // Reverse order of indices when compared to corresponding Clojure function
  return vec2(sun_elevation_index, height_index);
}
