#version 410 core

uniform float radius;
uniform float max_height;
uniform float object_distance;
uniform vec3 light_direction;

vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
vec4 cloud_outer(vec3 origin, vec3 direction);
vec4 cloud_point(vec3 origin, vec3 direction, vec3 point);
vec4 plume_outer(vec3 object_origin, vec3 object_direction);
vec4 plume_point(vec3 object_origin, vec3 object_direction, vec3 object_point);
vec3 attenuate(vec3 light_direction, vec3 start, vec3 point, vec3 incoming);

// Cloud and plume mixed overlay.
// origin: world coordinate of camera position
// direction: world coordinate of viewing direction for current pixel rendered by fragment shader.
// point: world coordinates of point of planet surface
// object_origin: camera position coordinates in object (plume) coordinate system.
// object_direction: viewing direction coordinates in object (plume) coordinate system.
// object_point: model surface point in object (plume) coordinate system.

<% (if (and (not planet-point) (not model-point)) %>
// Shader to render overlay with clouds, then plume, and then clouds again with outer space in the background.
vec4 cloud_plume_cloud(vec3 origin, vec3 direction, vec3 object_origin, vec3 object_direction)
<% ) %>
<% (if (and planet-point (not model-point)) %>
// Shader to render overlay with clouds, then plume, and then clouds again with planet in the background.
vec4 cloud_plume_cloud_point(vec3 origin, vec3 direction, vec3 point, vec3 object_origin, vec3 object_direction)  // TODO: add object point
<% ) %>
<% (if model-point %>
// Shader to render overlay with clouds, then plume with model in the background.
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
  vec2 atmosphere = ray_sphere(vec3(0, 0, 0), radius + max_height, origin, direction);
  atmosphere.y = min(atmosphere.y, object_distance - atmosphere.x);
  if (atmosphere.y > 0) {
    vec3 start = origin + direction * atmosphere.x;
    vec3 end = origin + direction * (atmosphere.x + atmosphere.y);
    result.rgb = attenuate(light_direction, start, end, result.rgb);
  };
  vec4 cloud_scatter = cloud_point(origin, direction, origin + direction * object_distance);
  result = vec4(cloud_scatter.rgb + result.rgb * (1.0 - cloud_scatter.a), 1.0 - (1.0 - cloud_scatter.a) * (1.0 - result.a));
  return result;
}
