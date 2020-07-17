import ast
import pickle
import asttokens

from preprocessing.traverse import PatternSubtreesExtractor
from preprocessing.loaders import load_pattern_by_pattern_id

if __name__ == '__main__':
    with open('../data/fragments-9-32.pickle', 'rb') as f:
        pattern = pickle.load(f)
    # pattern = load_pattern_by_pattern_id(pattern_id=32)

    # Load pattern's data and about certain fragment
    fragment_id = 1039447
    graphs = pattern['fragments_graphs'][fragment_id]
    old_method, new_method = pattern['old_methods'][fragment_id], pattern['new_methods'][fragment_id]
    cg = pattern['change_graphs'][fragment_id]

    # Extract node_ids corresponding to pattern
    pattern_edges = graphs[0].get_edges()
    pattern_nodes_ids = set()
    for e in pattern_edges:
        pattern_nodes_ids.add(int(e.get_source()))
        pattern_nodes_ids.add(int(e.get_destination()))
    pattern_nodes = [node for node in cg.nodes if node.id in pattern_nodes_ids]

    # Build AST of method before changes
    old_method_ast = ast.parse(old_method.get_source(), mode='exec')
    old_method_tokenized_ast = asttokens.ASTTokens(old_method.get_source(), tree=old_method_ast)

    # Extract only changed AST subtrees from pattern
    extractor = PatternSubtreesExtractor(pattern_nodes)
    subtrees = extractor.get_changed_subtrees(old_method_tokenized_ast.tree)

    # Locate in target method
    with open('examples/103.py', 'rb') as f:
        target_method_src = f.read()
    target_method_ast = ast.parse(target_method_src, mode='exec')
    target_method_tokenized_ast = asttokens.ASTTokens(target_method_src, tree=target_method_ast)

    print('Done')
