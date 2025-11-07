#version 410 core

uniform mat4 world_to_object;
uniform float object_distance;

vec4 cloud_segment(vec3 direction, vec3 start, vec2 segment);
<% (if clouds-behind %>
vec4 plume_outer(vec3 point, vec3 direction);
<% %>
vec4 plume_point(vec3 point, vec3 direction);
<% ) %>

vec4 cloud_plume_segment(vec3 direction, vec3 start, vec2 segment)
{
<% (if clouds-behind %>
  vec4 plume = plume_outer(vec3(0, 0, 0), mat3(world_to_object) * direction);
<% %>
  vec4 plume = plume_point(vec3(0, 0, 0), mat3(world_to_object) * direction);
<% ) %>
<% (if clouds-behind %>
  vec4 result;
  float segment_back_start = max(object_distance, segment.x);
  float segment_back_end = segment.x + segment.y;
  vec2 segment_back = vec2(segment_back_start, segment_back_end - segment_back_start);
  if (segment_back.y > 0)
    result = cloud_segment(direction, vec3(0, 0, 0), segment_back);
  else
    result = vec4(0, 0, 0, 0);
  result = vec4(plume.rgb + result.rgb * (1.0 - plume.a), 1.0 - (1.0 - plume.a) * (1.0 - result.a));
<% %>
  vec4 result = plume;
<% ) %>
  float segment_front_start = segment.x;
  float segment_front_end = min(object_distance, segment.x + segment.y);
  vec2 segment_front = vec2(segment_front_start, segment_front_end - segment_front_start);
  if (segment_front.y > 0) {
    vec4 cloud_scatter = cloud_segment(direction, vec3(0, 0, 0), segment_front);
    result = vec4(cloud_scatter.rgb + result.rgb * (1.0 - cloud_scatter.a), 1.0 - (1.0 - cloud_scatter.a) * (1.0 - result.a));
  };
  return result;
}
