#version 410 core

float M_PI = 3.14159265358;

float elevation_to_index(int size, float elevation, float horizon_angle, float power, bool sky, bool ground) {
  int ground_size = (size - 1) / 2;
  int sky_size = size / 2 + 1;
  float horizon = 0.5 * M_PI + horizon_angle;
  float result;
  if (sky && elevation > horizon)
    elevation = horizon;
  if (ground && elevation < horizon)
    elevation = horizon;
  if (!ground && elevation <= horizon) // sky
    result = (1 - pow(1 - elevation / horizon, 1 / power)) * (sky_size - 1);
  else // ground
    result = sky_size + pow((elevation - horizon) / (0.5 * M_PI - horizon_angle), 1 / power) * (ground_size - 1);
  return result / (size - 1);
}
