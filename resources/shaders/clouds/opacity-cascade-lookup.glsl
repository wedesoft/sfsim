#version 410 core

uniform mat4 world_to_camera;
uniform vec3 light_direction;

<% (doseq [i (range n)] %>
uniform sampler3D opacity<%= i %>;
uniform mat4 world_to_shadow_map<%= i %>;
uniform float depth<%= i %>;
uniform float bias<%= i %>;
<% ) %>
<% (doseq [i (range (inc n))] %>
uniform float split<%= i %>;
<% ) %>

float <%= base-function %>(sampler3D layers, float depth, vec4 opacity_map_coords);
float opacity_cascade_lookup(vec4 point)
{
  float z = -(world_to_camera * point).z;
<% (doseq [i (range n)] %>
  if (z <= split<%= (inc i) %>) {
    vec4 map_coords = world_to_shadow_map<%= i %> * (point + bias<%= i %> * vec4(light_direction, 0));
    return <%= base-function %>(opacity<%= i %>, depth<%= i %>, map_coords);
  };
<% ) %>
  return 1.0;
}
