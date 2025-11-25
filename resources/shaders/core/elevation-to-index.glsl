#version 450 core

uniform float radius;
uniform float max_height;
float limit_quot(float a, float b, float lower, float upper);
float horizon_distance(float ground_radius, float radius_sqr);

float elevation_to_index(vec3 point, vec3 direction, bool above_horizon)
{
  float point_radius = length(point);
  float sin_elevation = dot(point, direction) / point_radius;
  float rho = horizon_distance(radius, point_radius * point_radius);
  float delta = point_radius * point_radius * sin_elevation * sin_elevation - rho * rho;
  if (above_horizon) {
    float top_radius = radius + max_height;
    float h = sqrt(top_radius * top_radius - radius * radius);
    return 0.5 - limit_quot(point_radius * sin_elevation - sqrt(max(0, delta + h * h)), 2 * rho + 2 * h, -0.5, 0.0);
  } else
    return 0.5 + limit_quot(point_radius * sin_elevation + sqrt(max(0, delta)), 2 * rho, -0.5, 0.0);
}
