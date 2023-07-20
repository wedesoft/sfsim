#version 410 core

uniform mat4 inverse_transform;

<% (doseq [i (range n)] %>
uniform sampler2DShadow shadow_map<%= i %>;
uniform mat4 shadow_map_matrix<%= i %>;
<% ) %>
<% (doseq [i (range (inc n))] %>
uniform float split<%= i %>;
<% ) %>

float <%= base-function %>(sampler2DShadow shadow_map, vec4 shadow_pos);

float shadow_cascade_lookup(vec4 point)
{
  float z = -(inverse_transform * point).z;
<% (doseq [i (range n)] %>
  if (z <= split<%= (inc i) %>) {
    vec4 shadow_pos = shadow_map_matrix<%= i %> * point;
    return <%= base-function %>(shadow_map<%= i %>, shadow_pos);
  };
<% ) %>
  return 1.0;
}
