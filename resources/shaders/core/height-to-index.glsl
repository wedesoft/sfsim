#version 410

uniform float radius;
uniform float polar_radius;
uniform float max_height;
vec3 polar_stretch(vec3 vector, float radius, float polar_radius);
float horizon_distance(float ground_radius, float radius_sqr);

float height_to_index(vec3 point)
{
  vec3 scaled_point = polar_stretch(point, radius, polar_radius);
  float current_horizon_distance = horizon_distance(radius, dot(scaled_point, scaled_point));
  float top_radius = radius + max_height;
  float max_horizon_distance = horizon_distance(radius, top_radius * top_radius);
  return current_horizon_distance / max_horizon_distance;
}
