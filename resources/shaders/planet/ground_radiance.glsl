#version 410 core

float M_PI = 3.14159265358;
uniform sampler2D transmittance;
uniform sampler2D surface_radiance;
uniform int elevation_size;
uniform int height_size;
uniform float albedo;
uniform float reflectivity;

bool is_above_horizon(vec3 point, vec3 direction);
vec2 transmittance_forward(vec3 point, vec3 direction, bool above_horizon);
vec4 interpolate_2d(sampler2D table, int size_y, int size_x, vec2 idx);

// Compute radiance of point on ground depending on illumination and atmospheric transmittance and scattering.
vec3 ground_radiance(vec3 point, vec3 light_direction, float water, float cos_incidence, float highlight,
                     vec3 land_color, vec3 water_color)
{
  bool above = is_above_horizon(point, light_direction);
  vec2 uv = transmittance_forward(point, light_direction, above);
  vec3 direct_light;
  if (above)
    direct_light = interpolate_2d(transmittance, height_size, elevation_size, uv).rgb;
  else
    direct_light = vec3(0, 0, 0);
  vec3 ambient_light = interpolate_2d(surface_radiance, height_size, elevation_size, uv).rgb;
  vec3 color = land_color * (1 - water) + water_color * water;
  return (albedo / M_PI) * color * (cos_incidence * direct_light + ambient_light) + (water * reflectivity * highlight) * direct_light;
}
