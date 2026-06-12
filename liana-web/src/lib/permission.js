export function flattenPermissions(tree = []) {
  const result = [];
  const walk = (nodes, parentPath = []) => {
    nodes.forEach((node) => {
      const current = [...parentPath, node];
      result.push({ ...node, ancestors: parentPath.map((item) => item.code) });
      if (node.children?.length) {
        walk(node.children, current);
      }
    });
  };
  walk(tree);
  return result;
}

export function filterMenus(tree = [], allowedCodes = []) {
  return tree
    .filter((node) => allowedCodes.includes(node.code) || node.type === 'menu')
    .map((node) => ({
      ...node,
      children: node.children ? filterMenus(node.children, allowedCodes) : [],
    }));
}
