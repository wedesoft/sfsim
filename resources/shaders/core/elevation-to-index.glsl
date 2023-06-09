#version 410 core

uniform float radius;
uniform float polar_radius;
uniform float max_height;
float limit_quot(float a, float b, float lower, float upper);
vec3 polar_stretch(vec3 vector, float radius, float polar_radius);
float horizon_distance(float ground_radius, float radius_sqr);

float elevation_to_index(vec3 point, vec3 direction, bool above_horizon)
{
  vec3 scaled_point = polar_stretch(point, radius, polar_radius);
  vec3 scaled_direction = normalize(polar_stretch(direction, radius, polar_radius));
  float point_radius = length(scaled_point);
  float sin_elevation = dot(scaled_point, scaled_direction) / point_radius;
  float rho = horizon_distance(radius, point_radius * point_radius);
  float delta = point_radius * point_radius * sin_elevation * sin_elevation - rho * rho;
  if (above_horizon) {
    float top_radius = radius + max_height;
    float h = sqrt(top_radius * top_radius - radius * radius);
    return 0.5 - limit_quot(point_radius * sin_elevation - sqrt(max(0, delta + h * h)), 2 * rho + 2 * h, -0.5, 0.0);
  } else
    return 0.5 + limit_quot(point_radius * sin_elevation + sqrt(max(0, delta)), 2 * rho, -0.5, 0.0);
}
