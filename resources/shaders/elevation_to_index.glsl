#version 410 core

float M_PI = 3.14159265358;

float elevation_to_index(int size, float elevation, float horizon_angle, float power) {
  int ground_size = (size - 1) / 2;
  int sky_size = size / 2 + 1;
  float horizon = 0.5 * M_PI + horizon_angle;
  if (elevation <= horizon) // sky
    return (0.5 + (1 - pow(1 - elevation / horizon, 1 / power)) * (sky_size - 1)) / size;
  else // ground
    return (0.5 + sky_size + pow((elevation - horizon) / (0.5 * M_PI - horizon_angle), 1 / power) * (ground_size - 1)) / size;
}
