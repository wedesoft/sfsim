#version 410 core

<% (doseq [i (range n)] %>uniform sampler3D opacity<%= i %>;
uniform sampler2D offset<%= i %>;
uniform mat4 shadow_map_matrix<%= i %>;
<% ) %>
<% (doseq [i (range (inc n))] %>uniform float split<%= i %>;
<% ) %>

float opacity_lookup(sampler2D offsets, sampler3D layers, vec3 opacity_map_coords, int size_z, int size_y, int size_x);
float opacity_cascade_lookup(vec4 point, int size_z, int size_y, int size_x)
{
  float z = point.z;
<% (doseq [i (range n)] %>if (z >= split<%= i %> && z <= split<%= (inc i) %>) {
    vec4 map_coords = shadow_map_matrix<%= i %> * point;
    return opacity_lookup(offset<%= i %>, opacity<%= i %>, map_coords.xyz, size_z, size_y, size_x);
  };
<% ) %>
  return 1.0;
}
