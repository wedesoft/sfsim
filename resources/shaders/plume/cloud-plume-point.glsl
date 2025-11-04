#version 410 core

uniform vec3 origin;
uniform float object_distance;

vec4 cloud_segment(vec3 direction, vec3 start, vec2 segment);
vec4 plume_point(vec3 point); // TODO: implement plume_point with atmospheric transmittance

vec4 cloud_plume_point(vec3 point)
{
  vec3 direction = normalize(point - origin);
  float dist = distance(origin, point);
  // TODO: atmosphere intersection and start point.
  vec4 result = cloud_segment(direction, vec3(0, 0, 0), vec2(object_distance, dist - object_distance));
  vec4 plume = plume_point(point);
  result = vec4(plume.rgb + result.rgb * (1.0 - plume.a), 1.0 - (1.0 - plume.a) * (1.0 - result.a));
  vec4 cloud_scatter = cloud_segment(direction, vec3(0, 0, 0), vec2(0, object_distance));
  return vec4(cloud_scatter.rgb + result.rgb * (1.0 - cloud_scatter.a), 1.0 - (1.0 - cloud_scatter.a) * (1.0 - result.a));
}
