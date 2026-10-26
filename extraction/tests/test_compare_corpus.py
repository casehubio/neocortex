"""Tests for compare_corpus.py — regression detection."""

from compare_corpus import compare, check_structural, check_field_coverage, check_content


def _corpus(nodes=None, edges=None, memories=None,
            entity_enrichments=None):
    c = {"nodes": nodes or [], "edges": edges or [], "memories": memories or []}
    if entity_enrichments is not None:
        c["entity_enrichments"] = entity_enrichments
    return c


def _node(name, subgraph_type="person", **kw):
    n = {"name": name, "subgraph_type": subgraph_type, "properties": {}}
    n.update(kw)
    return n


def _memory(text="A memory", domain="experience", **kw):
    m = {"text": text, "domain": domain}
    m.update(kw)
    return m


def _edge(source, target, edge_type="influenced-by"):
    return {"source": source, "target": target, "type": edge_type}


class TestStructural:

    def test_identical_passes(self):
        c = _corpus(nodes=[_node("A")], edges=[_edge("A", "B")])
        result = check_structural(c, c)
        assert result["status"] == "pass"
        assert result["regressions"] == 0

    def test_node_loss_fails(self):
        baseline = _corpus(nodes=[_node("A"), _node("B")])
        candidate = _corpus(nodes=[_node("A")])
        result = check_structural(baseline, candidate)
        assert result["status"] == "fail"
        assert result["regressions"] >= 1

    def test_node_gain_warns(self):
        baseline = _corpus(nodes=[_node("A")])
        candidate = _corpus(nodes=[_node("A"), _node("B")])
        result = check_structural(baseline, candidate)
        assert result["status"] == "warn"
        assert result["regressions"] == 0

    def test_memory_domain_tracked(self):
        baseline = _corpus(memories=[_memory(domain="experience"), _memory(domain="mood")])
        candidate = _corpus(memories=[_memory(domain="experience")])
        result = check_structural(baseline, candidate)
        diffs = [d for d in result["diffs"] if d["category"] == "memory_domain"]
        assert len(diffs) >= 1

    def test_edge_count_tracked(self):
        baseline = _corpus(edges=[_edge("A", "B"), _edge("C", "D")])
        candidate = _corpus(edges=[_edge("A", "B")])
        result = check_structural(baseline, candidate)
        edge_diffs = [d for d in result["diffs"] if d["category"] == "edge_count"]
        assert len(edge_diffs) == 1
        assert edge_diffs[0]["status"] == "regressed"

    def test_type_distribution_change(self):
        baseline = _corpus(nodes=[_node("A", "goal"), _node("B", "goal")])
        candidate = _corpus(nodes=[_node("A", "goal"), _node("B", "person")])
        result = check_structural(baseline, candidate)
        assert len(result["diffs"]) >= 1


class TestFieldCoverage:

    def test_identical_coverage_passes(self):
        nodes = [_node("A", pad={"pleasure": 0.5})]
        result = check_field_coverage(_corpus(nodes=nodes), _corpus(nodes=nodes))
        assert result["status"] == "pass"

    def test_coverage_drop_fails(self):
        baseline = _corpus(nodes=[_node("A", pad={"pleasure": 0.5})])
        candidate = _corpus(nodes=[_node("A")])
        result = check_field_coverage(baseline, candidate)
        assert result["status"] == "fail"
        assert len(result["coverage_drops"]) >= 1

    def test_memory_enrichment_drop(self):
        baseline = _corpus(memories=[
            _memory(occ_emotions=["joy"]),
            _memory(occ_emotions=["fear"]),
        ])
        candidate = _corpus(memories=[_memory(), _memory()])
        result = check_field_coverage(baseline, candidate)
        assert result["status"] == "fail"
        drops = [d for d in result["coverage_drops"] if d["field"] == "occ_emotions"]
        assert len(drops) == 1

    def test_small_drop_within_tolerance(self):
        pad = {"pleasure": 0.5}
        baseline_nodes = [_node(f"N{i}", pad=pad) for i in range(20)]
        baseline_nodes.append(_node("Extra", pad=pad))
        candidate_nodes = [_node(f"N{i}", pad=pad) for i in range(20)]
        candidate_nodes.append(_node("Extra"))
        result = check_field_coverage(_corpus(nodes=baseline_nodes), _corpus(nodes=candidate_nodes))
        assert result["status"] == "pass"


class TestContent:

    def test_identical_passes(self):
        c = _corpus(nodes=[_node("A")], entity_enrichments={"A": {}})
        result = check_content(c, c)
        assert result["status"] == "pass"

    def test_removed_nodes_fail(self):
        baseline = _corpus(nodes=[_node("A"), _node("B")])
        candidate = _corpus(nodes=[_node("A")])
        result = check_content(baseline, candidate)
        assert result["status"] == "fail"
        assert "B" in result["nodes_removed"]

    def test_added_nodes_ok(self):
        baseline = _corpus(nodes=[_node("A")])
        candidate = _corpus(nodes=[_node("A"), _node("B")])
        result = check_content(baseline, candidate)
        assert "B" in result["nodes_added"]
        assert result["status"] != "fail"

    def test_pad_change_warns(self):
        baseline = _corpus(nodes=[_node("A", pad={"pleasure": 0.5, "arousal": 0.3})])
        candidate = _corpus(nodes=[_node("A", pad={"pleasure": 0.9, "arousal": 0.3})])
        result = check_content(baseline, candidate)
        assert result["status"] == "warn"
        assert len(result["pad_changes"]) == 1
        assert result["pad_changes"][0]["dimension"] == "pleasure"

    def test_missing_enrichments_fail(self):
        baseline = _corpus(entity_enrichments={"Frida": {"drive_baselines": {}}})
        candidate = _corpus(entity_enrichments={})
        result = check_content(baseline, candidate)
        assert result["status"] == "fail"
        assert "Frida" in result["missing_enrichments"]

    def test_no_enrichments_ok(self):
        result = check_content(_corpus(), _corpus())
        assert result["status"] == "pass"


class TestReport:

    def test_report_format(self):
        report = compare(_corpus(), _corpus())
        assert report["format"] == "regression-report/v1"
        assert report["summary"]["total"] == 3
        assert "checks" in report

    def test_clean_comparison(self):
        c = _corpus(nodes=[_node("A")], memories=[_memory()])
        report = compare(c, c)
        assert report["summary"]["failed"] == 0

    def test_regression_detected(self):
        baseline = _corpus(nodes=[_node("A"), _node("B")])
        candidate = _corpus(nodes=[_node("A")])
        report = compare(baseline, candidate)
        assert report["summary"]["failed"] >= 1
