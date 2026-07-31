import numpy as np
import torch
from torch.utils.data import Dataset, DataLoader
from typing import List, Tuple
from sklearn.model_selection import StratifiedShuffleSplit
from evaluation.strategy_classifier.config import HyperParams


def per_replay_split(
    replay_ids: List[int], labels: List[int], seed: int = 42,
) -> Tuple[List[int], List[int], List[int]]:
    ids = np.array(replay_ids)
    lbls = np.array(labels)

    try:
        sss1 = StratifiedShuffleSplit(n_splits=1, test_size=0.2, random_state=seed)
        train_val_idx, test_idx = next(sss1.split(ids, lbls))

        sss2 = StratifiedShuffleSplit(n_splits=1, test_size=0.125, random_state=seed)
        train_idx, val_idx = next(sss2.split(ids[train_val_idx], lbls[train_val_idx]))
    except ValueError:
        from sklearn.model_selection import ShuffleSplit
        ss1 = ShuffleSplit(n_splits=1, test_size=0.2, random_state=seed)
        train_val_idx, test_idx = next(ss1.split(ids))

        ss2 = ShuffleSplit(n_splits=1, test_size=0.125, random_state=seed)
        train_idx, val_idx = next(ss2.split(ids[train_val_idx]))

    return (
        ids[train_val_idx[train_idx]].tolist(),
        ids[train_val_idx[val_idx]].tolist(),
        ids[test_idx].tolist(),
    )


class StrategyDataset(Dataset):
    def __init__(self, samples: List[Tuple[np.ndarray, np.ndarray, int]]):
        self.samples = samples

    def __len__(self):
        return len(self.samples)

    def __getitem__(self, idx):
        temporal, map_feat, label = self.samples[idx]
        return (
            torch.from_numpy(temporal),
            torch.from_numpy(map_feat),
            label,
        )


def create_dataloaders(
    train_samples, val_samples, test_samples, hp: HyperParams,
) -> Tuple[DataLoader, DataLoader, DataLoader]:
    train_ds = StrategyDataset(train_samples)
    val_ds = StrategyDataset(val_samples)
    test_ds = StrategyDataset(test_samples)
    return (
        DataLoader(train_ds, batch_size=hp.batch_size, shuffle=True),
        DataLoader(val_ds, batch_size=hp.batch_size, shuffle=False),
        DataLoader(test_ds, batch_size=hp.batch_size, shuffle=False),
    )
