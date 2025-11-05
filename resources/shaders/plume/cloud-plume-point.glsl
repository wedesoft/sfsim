#version 410 core

uniform float radius;
uniform float max_height;
uniform vec3 origin;
uniform float object_distance;

vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
vec4 cloud_segment(vec3 direction, vec3 start, vec2 segment);
vec4 plume_point(vec3 point); // TODO: implement plume_point with atmospheric transmittance

vec4 cloud_plume_point(vec3 point)
{
  vec3 direction = normalize(point - origin);
  float dist = distance(origin, point);
  vec2 atmosphere_intersection = ray_sphere(vec3(0, 0, 0), radius + max_height, origin, direction);
  vec4 result = vec4(0, 0, 0, 0);
  float segment_back_start = max(object_distance, atmosphere_intersection.x);
  float segment_back_end = min(dist, atmosphere_intersection.x + atmosphere_intersection.y);
  vec2 segment_back = vec2(segment_back_start, segment_back_end - segment_back_start);
  if (segment_back.y > 0)
    result = cloud_segment(direction, vec3(0, 0, 0), segment_back);
  else
    result = vec4(0, 0, 0, 0);
  // TODO: use space ship or camera coordinates for higher precision?
  vec4 plume = plume_point(point);
  result = vec4(plume.rgb + result.rgb * (1.0 - plume.a), 1.0 - (1.0 - plume.a) * (1.0 - result.a));
  float segment_front_start = atmosphere_intersection.x;
  float segment_front_end = min(object_distance, atmosphere_intersection.x + atmosphere_intersection.y);
  vec2 segment_front = vec2(segment_front_start, segment_front_end - segment_front_start);
  if (segment_front.y > 0) {
    vec4 cloud_scatter = cloud_segment(direction, vec3(0, 0, 0), segment_front);
    result = vec4(cloud_scatter.rgb + result.rgb * (1.0 - cloud_scatter.a), 1.0 - (1.0 - cloud_scatter.a) * (1.0 - result.a));
  };
  return result;
}
