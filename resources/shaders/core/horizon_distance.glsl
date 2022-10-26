#version 410

float horizon_distance(float ground_radius, float radius_sqr)
{
  return sqrt(radius_sqr - ground_radius * ground_radius);
}
