#version 410 core

uniform mat4 world_to_camera;

<% (doseq [i (range n)] %>
uniform sampler2DShadow shadow_map<%= i %>;
uniform mat4 world_to_shadow_map<%= i %>;
<% ) %>
<% (doseq [i (range (inc ^long n))] %>
uniform float split<%= i %>;
<% ) %>

float <%= base-function %>(sampler2DShadow shadow_map, vec4 shadow_pos);

float shadow_cascade_lookup(vec4 point)
{
  float z = -(world_to_camera * point).z;
<% (doseq [i (range n)] %>
  if (z <= split<%= (inc ^long i) %>) {
    vec4 shadow_pos = world_to_shadow_map<%= i %> * point;
    return <%= base-function %>(shadow_map<%= i %>, shadow_pos);
  };
<% ) %>
  return 1.0;
}
