#version 410 core

uniform mat4 inverse_transform;

<% (doseq [i (range n)] %>
uniform sampler3D opacity<%= i %>;
uniform sampler2D offset<%= i %>;
uniform mat4 shadow_map_matrix<%= i %>;
uniform float depth<%= i %>;
<% ) %>
<% (doseq [i (range (inc n))] %>
uniform float split<%= i %>;
<% ) %>

float <%= base-function %>(sampler2D offsets, sampler3D layers, float depth, vec3 opacity_map_coords);
float opacity_cascade_lookup(vec4 point)
{
  float z = -(inverse_transform * point).z;
<% (doseq [i (range n)] %>
  if (z <= split<%= (inc i) %>) {
    vec4 map_coords = shadow_map_matrix<%= i %> * point;
    return <%= base-function %>(offset<%= i %>, opacity<%= i %>, depth<%= i %>, map_coords.xyz);
  };
<% ) %>
  return 1.0;
}
