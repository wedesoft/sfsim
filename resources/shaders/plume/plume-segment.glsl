#version 450 core

uniform float engine_step;

vec2 plume_box(vec3 origin, vec3 direction);
vec4 plume_transfer(vec3 point, float plume_step, vec4 plume_scatter);
float sampling_offset();

<% (if outer %>
vec4 plume_outer(vec3 object_origin, vec3 object_direction)
<% %>
vec4 plume_point(vec3 object_origin, vec3 object_direction, vec3 object_point)
<% ) %>
{
  vec2 segment = plume_box(object_origin, object_direction);
<% (if (not outer) %>
  segment.y = min(segment.y, distance(object_point, object_origin) - segment.x);
<% ) %>
  vec4 result = vec4(0, 0, 0, 1);
  float x = segment.x + sampling_offset() * engine_step;
  while (x < segment.x + segment.y) {
    vec3 point = object_origin + x * object_direction;
    result = plume_transfer(point, engine_step, result);
    x += engine_step;
  };
  return vec4(result.rgb, 1 - result.a);
}
