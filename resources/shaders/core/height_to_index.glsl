#version 410

uniform float radius;
uniform float max_height;
float horizon_distance(float ground_radius, float radius_sqr);
float height_to_index(vec3 point)
{
  float current_horizon_distance = horizon_distance(radius, dot(point, point));
  float top_radius = radius + max_height;
  float max_horizon_distance = horizon_distance(radius, top_radius * top_radius);
  return current_horizon_distance / max_horizon_distance;
}
