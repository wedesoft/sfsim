#version 410 core

float M_PI = 3.14159265358;
uniform float albedo;
uniform float reflectivity;

bool is_above_horizon(vec3 point, vec3 direction);
vec3 transmittance_outer(vec3 point, vec3 direction);
vec3 surface_radiance_function(vec3 point, vec3 light_direction);

// Compute radiance of point on ground depending on illumination and atmospheric transmittance and scattering.
vec3 ground_radiance(vec3 point, vec3 light_direction, float water, float incidence_fraction, float highlight,
                     vec3 land_color, vec3 water_color)
{
  bool above = is_above_horizon(point, light_direction);
  vec3 direct_light;
  if (above)
    direct_light = transmittance_outer(point, light_direction);
  else
    direct_light = vec3(0, 0, 0);
  vec3 ambient_light = surface_radiance_function(point, light_direction);
  vec3 color = land_color * (1 - water) + water_color * water;
  return (albedo / M_PI) * color * (incidence_fraction * direct_light + ambient_light) + (water * reflectivity * highlight) * direct_light;
}
