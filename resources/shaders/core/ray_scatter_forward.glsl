#version 410 core

float M_PI = 3.14159265358;

uniform int elevation_size;
uniform int light_elevation_size;
uniform float radius;
uniform float max_height;

float clip_angle(float angle);
float horizon_angle(vec3 point);
float elevation_to_index(int elevation_size, float elevation, float horizon_angle, bool above_horizon);
bool is_above_horizon(float radius, vec3 point, vec3 direction);
mat3 oriented_matrix(vec3 n);

// Convert input parameters to 4D index for lookup in precomputed ray scatter table.
vec4 ray_scatter_forward(vec3 point, vec3 direction, vec3 light_direction, bool above_horizon)
{
  float dist = length(point);
  vec3 normal = point / dist;
  float horizon = horizon_angle(point);
  mat3 oriented = oriented_matrix(normal);
  vec3 direction_rotated = oriented * direction;
  vec3 light_rotated = oriented * light_direction;
  float light_azimuth = atan(light_rotated.z, light_rotated.y);
  float direction_azimuth = atan(direction_rotated.z, direction_rotated.y);
  float light_heading = abs(clip_angle(light_azimuth - direction_azimuth));
  float light_heading_index = light_heading / M_PI;
  float cos_light_elevation = dot(normal, light_direction);
  float light_elevation = acos(cos_light_elevation);
  bool light_above = is_above_horizon(radius, point, light_direction);
  float light_elevation_index = elevation_to_index(light_elevation_size, light_elevation, horizon, light_above);
  float cos_elevation = dot(normal, direction);
  float elevation = acos(cos_elevation);
  float elevation_index = elevation_to_index(elevation_size, elevation, horizon, above_horizon);
  float height = dist - radius;
  float height_index = height / max_height;
  return vec4(light_heading_index, light_elevation_index, elevation_index, height_index);
}
