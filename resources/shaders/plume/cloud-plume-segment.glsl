#version 410 core

uniform float radius;
uniform float max_height;
uniform float object_distance;

vec4 cloud_outer(vec3 origin, vec3 direction);
vec4 cloud_point(vec3 origin, vec3 direction, vec3 point);
vec4 plume_outer(vec3 object_origin, vec3 object_direction);
vec4 plume_point(vec3 object_origin, vec3 object_direction, vec3 object_point);

<% (if (and (not planet-point) (not model-point)) %>
vec4 cloud_plume_cloud(vec3 origin, vec3 direction, vec3 object_origin, vec3 object_direction)
<% ) %>
<% (if (and planet-point (not model-point)) %>
vec4 cloud_plume_cloud_point(vec3 origin, vec3 direction, vec3 point, vec3 object_origin, vec3 object_direction)
<% ) %>
<% (if model-point %>
vec4 cloud_plume_point(vec3 origin, vec3 direction, vec3 object_origin, vec3 object_direction, vec3 object_point)
<% ) %>
{
<% (if (not model-point) %>
  vec4 plume = plume_outer(object_origin, object_direction);
<% %>
  vec4 plume = plume_point(object_origin, object_direction, object_point);
<% ) %>
<% (if (and (not planet-point) (not model-point)) %>
  vec4 result = cloud_outer(origin + direction * object_distance, direction);
  result = vec4(plume.rgb + result.rgb * (1.0 - plume.a), 1.0 - (1.0 - plume.a) * (1.0 - result.a));
<% ) %>
<% (if (and planet-point (not model-point)) %>
  vec4 result = cloud_point(origin + direction * object_distance, direction, point);
  result = vec4(plume.rgb + result.rgb * (1.0 - plume.a), 1.0 - (1.0 - plume.a) * (1.0 - result.a));
<% ) %>
<% (if model-point %>
  vec4 result = plume;
<% ) %>
  vec4 cloud_scatter = cloud_point(origin, direction, origin + direction * object_distance);
  result = vec4(cloud_scatter.rgb + result.rgb * (1.0 - cloud_scatter.a), 1.0 - (1.0 - cloud_scatter.a) * (1.0 - result.a));
  return result;
}
