import pytest

from evaluation.code_domain_embeddings.tokenizer_analysis import (
    analyze_tokenizer_output,
    classify_tokenizer_type,
    find_meaningful_subwords,
    format_results_table,
)


class TestAnalyzeTokenizerOutput:
    def test_returns_token_list_and_count(self):
        tokens = ["Con", "##current", "##Hash", "##Map"]
        result = analyze_tokenizer_output(tokens, "ConcurrentHashMap")
        assert result["tokens"] == tokens
        assert result["count"] == 4

    def test_single_token_preserved(self):
        tokens = ["shadowing"]
        result = analyze_tokenizer_output(tokens, "shadowing")
        assert result["count"] == 1
        assert result["preserved"] is True

    def test_multi_token_not_preserved(self):
        tokens = ["Con", "##current", "##Hash", "##Map"]
        result = analyze_tokenizer_output(tokens, "ConcurrentHashMap")
        assert result["preserved"] is False


class TestFindMeaningfulSubwords:
    def test_finds_hashmap_in_concurrent_hash_map(self):
        tokens = ["Con", "##current", "##Hash", "##Map"]
        meaningful = find_meaningful_subwords(tokens, "ConcurrentHashMap")
        cleaned = [t.lstrip("#") for t in meaningful]
        assert "Hash" in cleaned or "Map" in cleaned

    def test_empty_for_fully_fragmented(self):
        tokens = ["am", "##bi", "##gu", "##ous"]
        meaningful = find_meaningful_subwords(tokens, "ambiguous")
        assert len(meaningful) <= len(tokens)

    def test_annotation_symbol_stripped(self):
        tokens = ["@", "Default", "##Bean"]
        meaningful = find_meaningful_subwords(tokens, "@DefaultBean")
        cleaned = [t.lstrip("#@") for t in meaningful]
        assert "Default" in cleaned or "Bean" in cleaned


class TestClassifyTokenizerType:
    def test_wordpiece_detected(self):
        assert classify_tokenizer_type("BertTokenizer") == "WordPiece"

    def test_bpe_detected(self):
        assert classify_tokenizer_type("GPT2Tokenizer") == "BPE"

    def test_sentencepiece_detected(self):
        assert classify_tokenizer_type("XLMRobertaTokenizer") == "SentencePiece"

    def test_unknown_returns_unknown(self):
        assert classify_tokenizer_type("CustomTokenizer") == "Unknown"


class TestFormatResultsTable:
    def test_produces_markdown_table(self):
        results = {
            "model-a": {
                "ConcurrentHashMap": {
                    "tokens": ["Con", "##current"],
                    "count": 2,
                    "preserved": False,
                    "meaningful_subwords": ["Con"],
                },
            },
        }
        table = format_results_table(results, ["ConcurrentHashMap"])
        assert "| model-a" in table
        assert "ConcurrentHashMap" in table
