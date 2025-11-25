#version 450 core

uniform sampler2D surface_radiance;
uniform int surface_sun_elevation_size;
uniform int surface_height_size;

vec2 surface_radiance_forward(vec3 point, vec3 light_direction);
vec3 interpolate_2d(sampler2D table, int size_y, int size_x, vec2 idx);

vec3 surface_radiance_function(vec3 point, vec3 light_direction)
{
  vec2 surface_radiance_index = surface_radiance_forward(point, light_direction);
  return interpolate_2d(surface_radiance, surface_height_size, surface_sun_elevation_size, surface_radiance_index);
}
