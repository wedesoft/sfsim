#version 410 core

float M_PI = 3.14159265358;

uniform int elevation_size;
uniform float elevation_power;

// Convert elevation value to a texture lookup index between 0 and 1.
float elevation_to_index(float elevation, float horizon_angle, bool above_horizon) {
  int ground_size = (elevation_size - 1) / 2;
  int sky_size = elevation_size / 2 + 1;
  float horizon = 0.5 * M_PI + horizon_angle;
  float result;
  if (above_horizon) // sky
    result = (1 - pow(1 - min(elevation, horizon) / horizon, 1 / elevation_power)) * (sky_size - 1);
  else // ground
    result = sky_size + pow((max(elevation, horizon) - horizon) / (0.5 * M_PI - horizon_angle), 1 / elevation_power) * (ground_size - 1);
  return result / (elevation_size - 1);
}
