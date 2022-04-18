#version 410 core

float M_PI = 3.14159265358;

float clip_angle(float angle);
float horizon_angle(vec3 point, float radius);
float elevation_to_index(int size, float elevation, float horizon_angle, float power, bool above_horizon);
bool sky_or_ground(float radius, vec3 point, vec3 direction);
mat3 oriented_matrix(vec3 n);

vec4 ray_scatter_forward(vec3 point, vec3 direction, vec3 light_direction, float radius, float max_height, int height_size,
                         int elevation_size, int light_elevation_size, int heading_size, float power, bool above_horizon)
{
  float dist = length(point);
  vec3 normal = point / dist;
  float horizon = horizon_angle(point, radius);
  mat3 oriented = oriented_matrix(normal);
  vec3 direction_rotated = oriented * direction;
  vec3 light_rotated = oriented * light_direction;
  float light_azimuth = atan(light_rotated.z, light_rotated.y);
  float direction_azimuth = atan(direction_rotated.z, direction_rotated.y);
  float light_heading = abs(clip_angle(light_azimuth - direction_azimuth));
  float light_heading_index = light_heading / M_PI;
  float cos_light_elevation = dot(normal, light_direction);
  float light_elevation = acos(cos_light_elevation);
  bool light_above = sky_or_ground(radius, point, light_direction);
  float light_elevation_index = elevation_to_index(light_elevation_size, light_elevation, horizon, power, light_above);
  float cos_elevation = dot(normal, direction);
  float elevation = acos(cos_elevation);
  float elevation_index = elevation_to_index(elevation_size, elevation, horizon, power, above_horizon);
  float height = dist - radius;
  float height_index = height / max_height;
  return vec4(light_heading_index, light_elevation_index, elevation_index, height_index);
}
