#version 410 core

uniform mat4 world_to_camera;

<% (doseq [i (range n)] %>
uniform sampler3D opacity<%= i %>;
uniform mat4 world_to_shadow_map<%= i %>;
uniform float depth<%= i %>;
<% ) %>
<% (doseq [i (range (inc n))] %>
uniform float split<%= i %>;
<% ) %>

float <%= base-function %>(sampler3D layers, float depth, vec4 opacity_map_coords);
float opacity_cascade_lookup(vec4 point)
{
  float z = -(world_to_camera * point).z;
<% (doseq [i (range n)] %>
//  if (z <= split<%= (inc i) %>) {
//    vec4 map_coords = world_to_shadow_map<%= i %> * point;
//    return <%= base-function %>(opacity<%= i %>, depth<%= i %>, map_coords);
//  };
<% ) %>
  vec4 map_coords = world_to_shadow_map0 * point;
  return <%= base-function %>(opacity0, depth0, map_coords);
  //if (gl_FragCoord.y <= 120)
  //  return abs(point.x - 6378000.0) / 1.0;
  //else if (gl_FragCoord.y <= 240)
  //  return 0.0;
  //else
  //  return 1.0;
}
