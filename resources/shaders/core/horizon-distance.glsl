#version 410

float horizon_distance(float ground_radius, float radius_sqr)
{
  return sqrt(max(0.0, radius_sqr - ground_radius * ground_radius));
}
